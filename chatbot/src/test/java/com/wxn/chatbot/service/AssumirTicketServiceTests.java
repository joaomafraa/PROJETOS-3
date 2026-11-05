package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.wxn.chatbot.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:assumir-ticket;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class AssumirTicketServiceTests {
    private static final AtomicLong IDS = new AtomicLong(1000);
    @Autowired private AssumirTicketService service;
    @Autowired private ConcluirTicketService conclusao;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    private TransactionTemplate tx;
    private long id;

    @BeforeEach
    void preparar() {
        id = IDS.getAndAdd(10);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            Empresa empresa = new Empresa(id, "WXN");
            Empresa outra = new Empresa(id + 1, "Outra");
            em.persist(empresa);
            em.persist(outra);
            Cliente cliente = new Cliente(id, "+5581999999999");
            cliente.vincularEmpresa(empresa);
            Chatbot bot = new Chatbot(id, "Assistente", empresa, "Olá");
            em.persist(cliente);
            em.persist(bot);
            em.persist(new Atendente(id, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 1, "Bruno", "bruno@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 2, "Externo", "externo@example.com", outra, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 3, "Admin", "admin@example.com", empresa, Atendente.PerfilAcesso.ADMINISTRADOR));
            Conversa conversa = new Conversa(id, cliente, empresa, bot);
            em.persist(conversa);
            em.persist(new Ticket(id, "WXN-" + id, "Suporte", Ticket.TipoSolicitacao.SUPORTE, conversa));
        });
    }

    @Test
    void persisteResponsavelEEstadoSemAlterarConversa() {
        var resultado = service.assumir(id, id);
        assertEquals(id, resultado.responsavelId());
        assertEquals(Ticket.StatusTicket.EM_ATENDIMENTO, resultado.status());
        verificarSalvo(id, Ticket.StatusTicket.EM_ATENDIMENTO);
        tx.executeWithoutResult(status -> assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO,
                em.find(Conversa.class, id).getStatus()));
    }

    @Test
    void rejeitaInexistentesEIdsInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.assumir(null, id));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(0L, id));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(id, -1L));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(id, null));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(Long.MAX_VALUE, id));
        assertThrows(SecurityException.class, () -> service.assumir(id, Long.MAX_VALUE));
        verificarSalvo(null, Ticket.StatusTicket.PENDENTE);
    }

    @Test
    void rejeitaOutraEmpresaEAtendenteInativo() {
        assertThrows(IllegalArgumentException.class, () -> service.assumir(id, id + 2));
        tx.executeWithoutResult(status -> em.find(Atendente.class, id)
                .desativar(em.find(Atendente.class, id + 3)));
        assertThrows(SecurityException.class, () -> service.assumir(id, id));
        verificarSalvo(null, Ticket.StatusTicket.PENDENTE);
    }

    @Test
    void rejeitaNovaAtribuicaoDeTicketEmAtendimentoOuConcluido() {
        service.assumir(id, id);
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id + 1));
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id));
        verificarSalvo(id, Ticket.StatusTicket.EM_ATENDIMENTO);
        tx.executeWithoutResult(status -> em.find(Ticket.class, id).concluir());
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id + 1));
        verificarSalvo(id, Ticket.StatusTicket.CONCLUIDO);
    }

    @Test
    void preservaAtribuicaoPendenteDeOutroAtendente() {
        tx.executeWithoutResult(status -> em.find(Ticket.class, id)
                .atribuirResponsavel(em.find(Atendente.class, id + 1)));
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id));
        verificarSalvo(id + 1, Ticket.StatusTicket.PENDENTE);
        service.assumir(id, id + 1);
        verificarSalvo(id + 1, Ticket.StatusTicket.EM_ATENDIMENTO);
    }

    @Test
    void somenteUmAtendenteAssumeEmTransacoesConcorrentes() throws Exception {
        CountDownLatch primeiroAssumiu = new CountDownLatch(1);
        CountDownLatch liberarCommit = new CountDownLatch(1);
        CountDownLatch segundoIniciou = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primeiro = executor.submit(() -> tx.executeWithoutResult(status -> {
                service.assumir(id, id);
                primeiroAssumiu.countDown();
                aguardar(liberarCommit);
            }));
            assertTrue(primeiroAssumiu.await(5, TimeUnit.SECONDS));
            Future<?> segundo = executor.submit(() -> {
                segundoIniciou.countDown();
                return service.assumir(id, id + 1);
            });
            assertTrue(segundoIniciou.await(5, TimeUnit.SECONDS));
            // O primeiro mantém a transação aberta: o segundo deve aguardar o bloqueio.
            assertThrows(TimeoutException.class, () -> segundo.get(300, TimeUnit.MILLISECONDS));
            liberarCommit.countDown();
            primeiro.get(5, TimeUnit.SECONDS);
            ExecutionException erro = assertThrows(ExecutionException.class,
                    () -> segundo.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, erro.getCause());
            verificarSalvo(id, Ticket.StatusTicket.EM_ATENDIMENTO);
        } finally {
            liberarCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concluiEPersisteDataSemEncerrarConversa() {
        service.assumir(id, id);
        var resultado = conclusao.concluir(id, id);
        assertEquals(Ticket.StatusTicket.CONCLUIDO, resultado.status());
        assertEquals(id, resultado.responsavelId());
        assertNotNull(resultado.dataConclusao());
        tx.executeWithoutResult(status -> {
            Ticket ticket = em.find(Ticket.class, id);
            assertEquals(Ticket.StatusTicket.CONCLUIDO, ticket.getStatus());
            assertEquals(resultado.dataConclusao().toEpochMilli(), ticket.getDataConclusao().toEpochMilli());
            assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, ticket.getConversaOrigem().getStatus());
        });
        assertThrows(IllegalStateException.class, () -> conclusao.concluir(id, id));
        tx.executeWithoutResult(status -> assertEquals(resultado.dataConclusao().toEpochMilli(),
                em.find(Ticket.class, id).getDataConclusao().toEpochMilli()));
    }

    @Test
    void rejeitaConclusaoPendenteEEntradaInvalida() {
        assertThrows(IllegalStateException.class, () -> conclusao.concluir(id, id));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(null, id));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(0L, id));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(id, null));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(id, -1L));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(Long.MAX_VALUE, id));
        assertThrows(SecurityException.class, () -> conclusao.concluir(id, Long.MAX_VALUE));
        verificarSalvo(null, Ticket.StatusTicket.PENDENTE);
    }

    @Test
    void rejeitaConclusaoPorOutroAtendenteAdministradorExternoOuInativo() {
        service.assumir(id, id);
        assertThrows(SecurityException.class, () -> conclusao.concluir(id, id + 1));
        assertThrows(SecurityException.class, () -> conclusao.concluir(id, id + 3));
        assertThrows(IllegalArgumentException.class, () -> conclusao.concluir(id, id + 2));
        tx.executeWithoutResult(status -> em.find(Atendente.class, id)
                .desativar(em.find(Atendente.class, id + 3)));
        assertThrows(SecurityException.class, () -> conclusao.concluir(id, id));
        verificarSalvo(id, Ticket.StatusTicket.EM_ATENDIMENTO);
        tx.executeWithoutResult(status -> assertNull(em.find(Ticket.class, id).getDataConclusao()));
    }

    @Test
    void rejeitaSegundaConclusaoConcorrente() throws Exception {
        service.assumir(id, id);
        CountDownLatch concluiu = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primeiro = executor.submit(() -> tx.executeWithoutResult(status -> {
                conclusao.concluir(id, id);
                concluiu.countDown();
                aguardar(liberar);
            }));
            assertTrue(concluiu.await(5, TimeUnit.SECONDS));
            Future<?> segundo = executor.submit(() -> conclusao.concluir(id, id));
            assertThrows(TimeoutException.class, () -> segundo.get(300, TimeUnit.MILLISECONDS));
            liberar.countDown();
            primeiro.get(5, TimeUnit.SECONDS);
            ExecutionException erro = assertThrows(ExecutionException.class,
                    () -> segundo.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, erro.getCause());
            verificarSalvo(id, Ticket.StatusTicket.CONCLUIDO);
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo de teste excedido.");
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(erro);
        }
    }

    private void verificarSalvo(Long responsavelId, Ticket.StatusTicket esperado) {
        tx.executeWithoutResult(status -> {
            Ticket salvo = em.find(Ticket.class, id);
            assertEquals(esperado, salvo.getStatus());
            if (responsavelId == null) assertNull(salvo.getResponsavel());
            else assertEquals(responsavelId, salvo.getResponsavel().getId());
        });
    }
}
