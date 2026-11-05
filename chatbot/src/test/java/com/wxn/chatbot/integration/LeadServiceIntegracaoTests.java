package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.NoSuchElementException;
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
import org.springframework.test.context.ActiveProfiles;

import com.wxn.chatbot.dto.LeadResumo;
import com.wxn.chatbot.dto.RegistrarLeadComando;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Lead;
import com.wxn.chatbot.repository.LeadRepository;
import com.wxn.chatbot.service.LeadDuplicadoException;
import com.wxn.chatbot.service.LeadService;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

// Sem @Transactional: cada chamada ao servico confirma de verdade, como em producao.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:leads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class LeadServiceIntegracaoTests {

    private static final long EMPRESA_A = 9001L;
    private static final long EMPRESA_B = 9002L;

    @Autowired private LeadService service;
    @Autowired private LeadRepository leads;
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
        leads.deleteAll();
        transacao.executeWithoutResult(s -> {
            entityManager.createQuery("delete from Empresa e where e.id in (:ids)")
                    .setParameter("ids", List.of(EMPRESA_A, EMPRESA_B)).executeUpdate();
        });
    }

    private RegistrarLeadComando comando(String contato, String necessidade) {
        return new RegistrarLeadComando("Maria", contato, Lead.Origem.SITE, necessidade);
    }

    @Test
    void registraEConsultaAposNovaLeitura() {
        LeadResumo criado = service.registrar(EMPRESA_A, comando("+55 81 99999-8888", "Orçamento"));

        assertNotNull(criado.id());
        LeadResumo lido = service.buscarPorId(EMPRESA_A, criado.id());
        assertEquals(EMPRESA_A, lido.empresaId());
        assertEquals("+5581999998888", lido.contato());
        assertEquals(Lead.Origem.SITE, lido.origem());
        assertEquals("Orçamento", lido.necessidade());
        assertNotNull(lido.dataCriacao());
        assertEquals(List.of(lido.id()),
                service.buscarPorContato(EMPRESA_A, "+55 (81) 99999 8888").stream()
                        .map(LeadResumo::id).toList());
    }

    @Test
    void geraIdsDistintosSemInformarId() {
        LeadResumo a = service.registrar(EMPRESA_A, comando("a@example.com", "Demonstração"));
        LeadResumo b = service.registrar(EMPRESA_A, comando("b@example.com", "Demonstração"));
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void mesmoContatoPodeRegistrarInteressesDiferentes() {
        service.registrar(EMPRESA_A, comando("maria@example.com", "Orçamento"));
        service.registrar(EMPRESA_A, comando("maria@example.com", "Demonstração"));

        assertEquals(2, service.buscarPorContato(EMPRESA_A, "MARIA@example.com").size());
    }

    @Test
    void rejeitaMesmoContatoComMesmaNecessidadeNaMesmaEmpresa() {
        service.registrar(EMPRESA_A, comando("maria@example.com", "Quero um Orçamento"));

        assertThrows(LeadDuplicadoException.class, () ->
                service.registrar(EMPRESA_A, comando(" MARIA@example.com ", "quero  um orçamento")));
        assertEquals(1, service.listar(EMPRESA_A).size());
    }

    @Test
    void mesmoContatoEInteressePodemExistirEmEmpresasDiferentes() {
        service.registrar(EMPRESA_A, comando("maria@example.com", "Orçamento"));
        service.registrar(EMPRESA_B, comando("maria@example.com", "Orçamento"));

        assertEquals(1, service.listar(EMPRESA_A).size());
        assertEquals(1, service.listar(EMPRESA_B).size());
    }

    @Test
    void isolaConsultasPorEmpresa() {
        LeadResumo deA = service.registrar(EMPRESA_A, comando("maria@example.com", "Orçamento"));

        assertTrue(service.listar(EMPRESA_B).isEmpty());
        assertTrue(service.buscarPorContato(EMPRESA_B, "maria@example.com").isEmpty());
        assertThrows(NoSuchElementException.class, () -> service.buscarPorId(EMPRESA_B, deA.id()));
    }

    @Test
    void rejeitaEmpresaInexistenteEDadosInvalidos() {
        assertThrows(NoSuchElementException.class,
                () -> service.registrar(777L, comando("maria@example.com", "Orçamento")));
        assertThrows(NoSuchElementException.class, () -> service.listar(777L));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(EMPRESA_A, comando("invalido", "Orçamento")));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(EMPRESA_A, comando("maria@example.com", " ")));
        assertThrows(NullPointerException.class, () -> service.registrar(EMPRESA_A,
                new RegistrarLeadComando("Maria", "maria@example.com", null, "Orçamento")));
        assertTrue(service.listar(EMPRESA_A).isEmpty());
    }

    @Test
    void registrosSimultaneosDoMesmoLeadGravamApenasUm() throws Exception {
        int tentativas = 6;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultados = new java.util.ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                Callable<Boolean> tarefa = () -> {
                    largada.await();
                    try {
                        service.registrar(EMPRESA_A, comando("maria@example.com", "Orçamento"));
                        return true;
                    } catch (LeadDuplicadoException e) {
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
            assertEquals(1, service.listar(EMPRESA_A).size());
        } finally {
            pool.shutdownNow();
        }
    }
}
