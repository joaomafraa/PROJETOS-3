package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.ClienteIdentificado;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.ClienteRepository;
import com.wxn.chatbot.service.IdentificacaoClienteService;
import com.wxn.chatbot.service.IdentificacaoClienteService.ClienteDuplicadoException;

import jakarta.persistence.EntityManager;

// HU07.1 e HU07.2. Sem @Transactional: cada chamada confirma de verdade, como em producao.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:identificacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class IdentificacaoClienteIntegracaoTests {

    private static final long EMPRESA_A = 8001L;
    private static final long EMPRESA_B = 8002L;
    private static final String TELEFONE = "+5581977770001";

    @Autowired private IdentificacaoClienteService service;
    @Autowired private ClienteRepository clientes;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transacao;

    @BeforeEach
    void criarEmpresas() {
        transacao.executeWithoutResult(s -> {
            entityManager.persist(new Empresa(EMPRESA_A, "Empresa A"));
            entityManager.persist(new Empresa(EMPRESA_B, "Empresa B"));
        });
    }

    @AfterEach
    void limpar() {
        transacao.executeWithoutResult(s -> {
            entityManager.createQuery("delete from Cliente c where c.empresa.id in (:ids)")
                    .setParameter("ids", List.of(EMPRESA_A, EMPRESA_B)).executeUpdate();
            entityManager.createQuery("delete from Empresa e where e.id in (:ids)")
                    .setParameter("ids", List.of(EMPRESA_A, EMPRESA_B)).executeUpdate();
        });
    }

    private ClienteIdentificado cadastrar(long empresaId, String telefone) {
        return service.cadastrar(empresaId, telefone, "Maria Silva", null);
    }

    private long contarClientes(long empresaId) {
        return transacao.execute(s -> entityManager.createQuery(
                "select count(c) from Cliente c where c.empresa.id = :empresa", Long.class)
                .setParameter("empresa", empresaId).getSingleResult());
    }

    private void gravarManual(long id, String telefone, long empresaId) {
        transacao.executeWithoutResult(s -> {
            Cliente cliente = new Cliente(id, telefone);
            cliente.vincularEmpresa(entityManager.find(Empresa.class, empresaId));
            clientes.saveAndFlush(cliente);
        });
    }

    // ---------- Repositorio e banco ----------

    @Test
    void repositorioBuscaPorEmpresaETelefoneSemVazarOutraEmpresa() {
        gravarManual(9_100_001L, TELEFONE, EMPRESA_A);

        assertTrue(clientes.findByEmpresaIdAndNumeroTelefone(EMPRESA_A, TELEFONE).isPresent());
        assertTrue(clientes.existsByEmpresaIdAndNumeroTelefone(EMPRESA_A, TELEFONE));
        assertTrue(clientes.findByEmpresaIdAndNumeroTelefone(EMPRESA_B, TELEFONE).isEmpty());
        assertFalse(clientes.existsByEmpresaIdAndNumeroTelefone(EMPRESA_B, TELEFONE));
    }

    @Test
    void bancoRejeitaTelefoneRepetidoNaMesmaEmpresaMesmoSemPassarPeloServico() {
        gravarManual(9_100_001L, TELEFONE, EMPRESA_A);

        assertThrows(DataIntegrityViolationException.class,
                () -> gravarManual(9_100_002L, TELEFONE, EMPRESA_A));
        assertEquals(1, contarClientes(EMPRESA_A));
    }

    @Test
    void sequenciaGeraIdsDiferentesEPulaIdManualJaUsado() {
        long proximo = transacao.execute(s -> clientes.proximoId()) + 1;
        gravarManual(proximo, "+5581977779999", EMPRESA_A);

        ClienteIdentificado primeiro = cadastrar(EMPRESA_A, "+5581977770001");
        ClienteIdentificado segundo = cadastrar(EMPRESA_A, "+5581977770002");

        assertNotEquals(proximo, primeiro.id());
        assertNotEquals(primeiro.id(), segundo.id());
        assertEquals(3, contarClientes(EMPRESA_A));
    }

    // ---------- HU07.1: localizar cliente ----------

    @Test
    void identificaClienteExistenteMesmoComTelefoneEmOutroFormato() {
        ClienteIdentificado criado = cadastrar(EMPRESA_A, TELEFONE);

        var encontrado = service.identificar(EMPRESA_A, "+55 (81) 97777-0001");

        assertTrue(encontrado.isPresent());
        assertEquals(criado.id(), encontrado.get().id());
        assertEquals("Maria Silva", encontrado.get().nome());
        assertEquals(TELEFONE, encontrado.get().numeroTelefone());
        assertFalse(encontrado.get().cadastradoAgora());
    }

    @Test
    void retornaVazioParaTelefoneDesconhecidoEParaOutraEmpresa() {
        assertTrue(service.identificar(EMPRESA_A, TELEFONE).isEmpty());

        cadastrar(EMPRESA_A, TELEFONE);

        assertTrue(service.identificar(EMPRESA_B, TELEFONE).isEmpty());
    }

    @Test
    void identificacaoRejeitaTelefoneOuEmpresaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.identificar(EMPRESA_A, "abc"));
        assertThrows(IllegalArgumentException.class, () -> service.identificar(EMPRESA_A, null));
        assertThrows(IllegalArgumentException.class, () -> service.identificar(null, TELEFONE));
        assertThrows(NoSuchElementException.class, () -> service.identificar(999999L, TELEFONE));
    }

    // ---------- HU07.2: cadastro minimo ----------

    @Test
    void cadastraClienteDesconhecidoEPersisteNaEmpresa() {
        ClienteIdentificado criado = service.cadastrar(EMPRESA_A, "+55 81 97777-0001",
                "  Maria Silva ", "Maria@Example.com");

        assertTrue(criado.id() > 0);
        assertTrue(criado.cadastradoAgora());
        assertEquals(EMPRESA_A, criado.empresaId());
        assertFalse(criado.identidadeConfirmada(), "Telefone nao comprova identidade.");

        Cliente lido = transacao.execute(s -> {
            Cliente c = entityManager.find(Cliente.class, criado.id());
            c.getEmpresa().getId();
            return c;
        });
        assertEquals(TELEFONE, lido.getNumeroTelefone());
        assertEquals("Maria Silva", lido.getNome());
        assertEquals("maria@example.com", lido.getEmail());
        assertEquals(EMPRESA_A, lido.getEmpresa().getId());
        assertFalse(lido.isIdentidadeConfirmada());
    }

    @Test
    void cadastroSemEmailEhAceito() {
        ClienteIdentificado criado = service.cadastrar(EMPRESA_A, TELEFONE, "Ana", " ");

        Cliente lido = transacao.execute(s -> entityManager.find(Cliente.class, criado.id()));
        assertNull(lido.getEmail());
    }

    @Test
    void impedeDuplicadoNaMesmaEmpresaMesmoComOutroFormato() {
        cadastrar(EMPRESA_A, TELEFONE);

        assertThrows(ClienteDuplicadoException.class, () -> cadastrar(EMPRESA_A, "+55 81 97777-0001"));
        assertEquals(1, contarClientes(EMPRESA_A));
    }

    @Test
    void permiteMesmoTelefoneEmOutraEmpresa() {
        ClienteIdentificado emA = cadastrar(EMPRESA_A, TELEFONE);
        ClienteIdentificado emB = cadastrar(EMPRESA_B, TELEFONE);

        assertNotEquals(emA.id(), emB.id());
        assertEquals(emA.id(), service.identificar(EMPRESA_A, TELEFONE).get().id());
        assertEquals(emB.id(), service.identificar(EMPRESA_B, TELEFONE).get().id());
    }

    @Test
    void rejeitaDadosInvalidosSemGravarNada() {
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(EMPRESA_A, "telefone", "Maria", null));
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(EMPRESA_A, TELEFONE, " ", null));
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(EMPRESA_A, TELEFONE, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar(EMPRESA_A, TELEFONE, "Maria", "sem-arroba"));

        assertEquals(0, contarClientes(EMPRESA_A));
    }

    @Test
    void rejeitaEmpresaInexistenteOuInvalida() {
        assertThrows(NoSuchElementException.class, () -> cadastrar(999999L, TELEFONE));
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(null, TELEFONE, "Maria", null));
        assertThrows(IllegalArgumentException.class, () -> cadastrar(0L, TELEFONE));
    }

    // ---------- Fluxo de entrada: identificar ou cadastrar ----------

    @Test
    void identificarOuCadastrarCriaNaPrimeiraVezEReaproveitaDepois() {
        ClienteIdentificado primeiro = service.identificarOuCadastrar(EMPRESA_A, TELEFONE, "Maria", null);
        ClienteIdentificado segundo = service.identificarOuCadastrar(EMPRESA_A, "+55 81 97777-0001", "Maria", null);

        assertTrue(primeiro.cadastradoAgora());
        assertFalse(segundo.cadastradoAgora());
        assertEquals(primeiro.id(), segundo.id());
        assertEquals(1, contarClientes(EMPRESA_A));
    }

    // ---------- Concorrencia ----------

    @Test
    void cadastrosSimultaneosDoMesmoTelefoneGravamApenasUm() throws Exception {
        int tentativas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                Callable<Boolean> tarefa = () -> {
                    largada.await();
                    try {
                        cadastrar(EMPRESA_A, TELEFONE);
                        return true;
                    } catch (ClienteDuplicadoException e) {
                        return false;
                    }
                };
                resultados.add(pool.submit(tarefa));
            }
            largada.countDown();
            int sucessos = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get()) {
                    sucessos++;
                }
            }
            assertEquals(1, sucessos);
            assertEquals(1, contarClientes(EMPRESA_A));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void identificarOuCadastrarSimultaneoDevolveOMesmoCliente() throws Exception {
        int tentativas = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<ClienteIdentificado>> resultados = new ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    return service.identificarOuCadastrar(EMPRESA_A, TELEFONE, "Maria", null);
                }));
            }
            largada.countDown();
            Set<Long> ids = new HashSet<>();
            int cadastrados = 0;
            for (Future<ClienteIdentificado> r : resultados) {
                ClienteIdentificado cliente = r.get();
                ids.add(cliente.id());
                if (cliente.cadastradoAgora()) {
                    cadastrados++;
                }
            }
            assertEquals(1, ids.size());
            assertEquals(1, cadastrados);
            assertEquals(1, contarClientes(EMPRESA_A));
        } finally {
            pool.shutdownNow();
        }
    }
}
