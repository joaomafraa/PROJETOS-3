package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.AtendenteResumo;
import com.wxn.chatbot.dto.CadastrarAtendenteComando;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.AtendenteRepository;

import jakarta.persistence.EntityManager;

// Sem @Transactional: cada chamada ao serviço confirma no banco, como em produção.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-atendentes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class AdministracaoAtendentesServiceTests {

    // IDs manuais altos para não colidir com os IDs gerados pela sequência.
    private static final AtomicLong IDS = new AtomicLong(500_000);

    @Autowired private AdministracaoAtendentesService service;
    @Autowired private AtendenteRepository atendentes;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;

    private TransactionTemplate tx;
    private long empresaA;
    private long empresaB;
    private long admin;
    private long admin2;
    private long comum;
    private long coordenador;
    private long adminB;
    private long comumB;

    @BeforeEach
    void preparar() {
        long base = IDS.getAndAdd(20);
        empresaA = base;
        empresaB = base + 1;
        admin = base;
        admin2 = base + 1;
        comum = base + 2;
        coordenador = base + 3;
        adminB = base + 4;
        comumB = base + 5;
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Empresa a = new Empresa(empresaA, "Empresa A");
            Empresa b = new Empresa(empresaB, "Empresa B");
            em.persist(a);
            em.persist(b);
            em.persist(new Atendente(admin, "Admin A", "admin@a.com", a, PerfilAcesso.ADMINISTRADOR));
            em.persist(new Atendente(admin2, "Admin A2", "admin2@a.com", a, PerfilAcesso.ADMINISTRADOR));
            em.persist(new Atendente(comum, "Ana", "ana@a.com", a, PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(coordenador, "Carla", "carla@a.com", a, PerfilAcesso.COORDENADOR));
            em.persist(new Atendente(adminB, "Admin B", "admin@b.com", b, PerfilAcesso.ADMINISTRADOR));
            em.persist(new Atendente(comumB, "Bruno", "bruno@b.com", b, PerfilAcesso.ATENDENTE));
        });
    }

    private Atendente lerDoBanco(long id) {
        return tx.execute(s -> {
            Atendente a = em.find(Atendente.class, id);
            a.getEmpresa().getId();
            return a;
        });
    }

    // ---------- cadastro ----------

    @Test
    void cadastraNaEmpresaDoAdministradorComIdGeradoEEmailNormalizado() {
        AtendenteResumo criado = service.cadastrar(admin,
                new CadastrarAtendenteComando("  Diego ", " Diego@A.com ", PerfilAcesso.ATENDENTE));

        assertNotNull(criado.id());
        Atendente salvo = lerDoBanco(criado.id());
        assertEquals("Diego", salvo.getNome());
        assertEquals("diego@a.com", salvo.getEmail());
        assertEquals(empresaA, salvo.getEmpresa().getId());
        assertEquals(PerfilAcesso.ATENDENTE, salvo.getPerfil());
        assertTrue(salvo.isAtivo());
    }

    @Test
    void idsGeradosSaoDistintos() {
        Long id1 = service.cadastrar(admin, new CadastrarAtendenteComando("X", "x@a.com", PerfilAcesso.ATENDENTE)).id();
        Long id2 = service.cadastrar(admin, new CadastrarAtendenteComando("Y", "y@a.com", PerfilAcesso.ATENDENTE)).id();
        assertNotEquals(id1, id2);
    }

    @Test
    void rejeitaEmailDuplicadoNaMesmaEmpresaMesmoComCaixaDiferente() {
        assertThrows(AtendenteDuplicadoException.class, () -> service.cadastrar(admin,
                new CadastrarAtendenteComando("Outra Ana", "ANA@a.com", PerfilAcesso.ATENDENTE)));
    }

    @Test
    void permiteMesmoEmailEmEmpresasDiferentes() {
        AtendenteResumo criado = service.cadastrar(adminB,
                new CadastrarAtendenteComando("Ana B", "ana@a.com", PerfilAcesso.ATENDENTE));
        assertEquals(empresaB, criado.empresaId());
    }

    @Test
    void rejeitaCadastroPorQuemNaoEAdministradorAtivo() {
        var comando = new CadastrarAtendenteComando("Z", "z@a.com", PerfilAcesso.ATENDENTE);
        assertThrows(SecurityException.class, () -> service.cadastrar(comum, comando));
        assertThrows(SecurityException.class, () -> service.cadastrar(coordenador, comando));
        assertThrows(SecurityException.class, () -> service.cadastrar(999_999_999L, comando));

        service.desativar(admin2, admin);
        assertThrows(SecurityException.class, () -> service.cadastrar(admin, comando));
        assertFalse(atendentes.existsByEmpresaIdAndEmail(empresaA, "z@a.com"));
    }

    @Test
    void rejeitaDadosInvalidosSemGravar() {
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(admin,
                new CadastrarAtendenteComando("Z", "invalido", PerfilAcesso.ATENDENTE)));
        assertThrows(IllegalArgumentException.class, () -> service.cadastrar(admin,
                new CadastrarAtendenteComando(" ", "z@a.com", PerfilAcesso.ATENDENTE)));
        assertThrows(NullPointerException.class, () -> service.cadastrar(admin,
                new CadastrarAtendenteComando("Z", "z@a.com", null)));
        assertThrows(NullPointerException.class, () -> service.cadastrar(admin, null));
        assertFalse(atendentes.existsByEmpresaIdAndEmail(empresaA, "z@a.com"));
    }

    @Test
    void cadastrosSimultaneosComMesmoEmailGeramApenasUmAtendente() throws Exception {
        var comando = new CadastrarAtendenteComando("Paralelo", "paralelo@a.com", PerfilAcesso.ATENDENTE);
        List<Future<Object>> resultados = executarJuntos(
                () -> service.cadastrar(admin, comando),
                () -> service.cadastrar(admin2, comando));

        int sucessos = 0;
        int duplicados = 0;
        for (Future<Object> f : resultados) {
            try {
                f.get();
                sucessos++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertInstanceOf(AtendenteDuplicadoException.class, e.getCause());
                duplicados++;
            }
        }
        assertEquals(1, sucessos);
        assertEquals(1, duplicados);
    }

    // ---------- consulta ----------

    @Test
    void listaSomenteAtendentesDaPropriaEmpresaComFiltroDeAtivos() {
        service.desativar(admin, comum);

        List<Long> todos = service.listar(admin, null).stream().map(AtendenteResumo::id).toList();
        assertEquals(List.of(admin, admin2, comum, coordenador), todos);
        assertEquals(List.of(comum), service.listar(admin, false).stream().map(AtendenteResumo::id).toList());
        assertFalse(service.listar(admin, true).stream().anyMatch(a -> a.id() == comum));
        assertTrue(service.listar(admin, null).stream().allMatch(a -> a.empresaId() == empresaA));
    }

    @Test
    void consultaRestritaAAdministradorEAEmpresa() {
        assertEquals("Ana", service.buscar(admin, comum).nome());
        assertThrows(NoSuchElementException.class, () -> service.buscar(admin, comumB));
        assertThrows(SecurityException.class, () -> service.buscar(comum, admin));
        assertThrows(SecurityException.class, () -> service.listar(comum, null));
    }

    // ---------- perfil ----------

    @Test
    void alteraPerfilEPersiste() {
        AtendenteResumo r = service.alterarPerfil(admin, comum, PerfilAcesso.COORDENADOR);
        assertEquals(PerfilAcesso.COORDENADOR, r.perfil());
        assertEquals(PerfilAcesso.COORDENADOR, lerDoBanco(comum).getPerfil());
    }

    @Test
    void administradorNaoAlteraOProprioPerfil() {
        assertThrows(IllegalArgumentException.class,
                () -> service.alterarPerfil(admin, admin, PerfilAcesso.ATENDENTE));
        assertEquals(PerfilAcesso.ADMINISTRADOR, lerDoBanco(admin).getPerfil());
    }

    @Test
    void naoAdministradorNaoAlteraPerfil() {
        assertThrows(SecurityException.class,
                () -> service.alterarPerfil(coordenador, comum, PerfilAcesso.ADMINISTRADOR));
        assertEquals(PerfilAcesso.ATENDENTE, lerDoBanco(comum).getPerfil());
    }

    @Test
    void rejeitaOperacoesEntreEmpresas() {
        assertThrows(NoSuchElementException.class,
                () -> service.alterarPerfil(admin, comumB, PerfilAcesso.ADMINISTRADOR));
        assertThrows(NoSuchElementException.class, () -> service.desativar(admin, comumB));
        assertThrows(NoSuchElementException.class, () -> service.ativar(adminB, comum));

        Atendente b = lerDoBanco(comumB);
        assertEquals(PerfilAcesso.ATENDENTE, b.getPerfil());
        assertTrue(b.isAtivo());
    }

    @Test
    void rejeitaPerfilNuloEAtendenteInexistente() {
        assertThrows(NullPointerException.class, () -> service.alterarPerfil(admin, comum, null));
        assertThrows(NoSuchElementException.class,
                () -> service.alterarPerfil(admin, 999_999_999L, PerfilAcesso.ATENDENTE));
        assertThrows(IllegalArgumentException.class, () -> service.desativar(admin, -1L));
    }

    @Test
    void administradoresRebaixandoUmAoOutroAoMesmoTempoNaoFicamAmbosRebaixados() throws Exception {
        List<Future<Object>> resultados = executarJuntos(
                () -> service.alterarPerfil(admin, admin2, PerfilAcesso.ATENDENTE),
                () -> service.alterarPerfil(admin2, admin, PerfilAcesso.ATENDENTE));

        int sucessos = 0;
        for (Future<Object> f : resultados) {
            try {
                f.get();
                sucessos++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertInstanceOf(SecurityException.class, e.getCause());
            }
        }
        assertEquals(1, sucessos);
        long administradores = List.of(admin, admin2).stream()
                .filter(id -> lerDoBanco(id).getPerfil() == PerfilAcesso.ADMINISTRADOR).count();
        assertEquals(1, administradores);
    }

    // ---------- ativação ----------

    @Test
    void desativaEReativaPersistindo() {
        assertFalse(service.desativar(admin, comum).ativo());
        assertFalse(lerDoBanco(comum).isAtivo());
        assertTrue(service.ativar(admin, comum).ativo());
        assertTrue(lerDoBanco(comum).isAtivo());
    }

    @Test
    void rejeitaEstadosInvalidosDeAtivacao() {
        assertThrows(IllegalStateException.class, () -> service.ativar(admin, comum));
        service.desativar(admin, comum);
        assertThrows(IllegalStateException.class, () -> service.desativar(admin, comum));
    }

    @Test
    void administradorNaoDesativaASiMesmo() {
        assertThrows(IllegalArgumentException.class, () -> service.desativar(admin, admin));
        assertTrue(lerDoBanco(admin).isAtivo());
    }

    @SafeVarargs
    private static List<Future<Object>> executarJuntos(Callable<Object>... tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.length);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Object>> futuros = new java.util.ArrayList<>();
            for (Callable<Object> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            for (Future<Object> f : futuros) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException ignorada) {
                    // verificada pelo teste
                }
            }
            return futuros;
        } finally {
            pool.shutdown();
        }
    }
}
