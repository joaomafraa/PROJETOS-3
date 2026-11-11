package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.llm.ProvedorLlm;
import com.wxn.chatbot.llm.RespostaAutomaticaService;
import com.wxn.chatbot.repository.ConversaRepository;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManager;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:humano;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class AtendimentoHumanoServiceTests {
    private static final AtomicLong IDS = new AtomicLong(5000);
    @Autowired private AtendimentoHumanoService service;
    @Autowired private FilaConversasService fila;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ConversaRepository conversas;
    @Autowired private RespostaAutomaticaService respostaReal;
    private TransactionTemplate tx;
    private long id;

    @BeforeEach
    void preparar() {
        id = IDS.getAndAdd(10);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Empresa empresa = new Empresa(id, "WXN");
            Empresa outra = new Empresa(id + 1, "Outra");
            em.persist(empresa);
            em.persist(outra);
            Cliente cliente = new Cliente(id, "+5581999999999");
            cliente.vincularEmpresa(empresa);
            Chatbot bot = new Chatbot(id, "Bot", empresa, "Olá");
            em.persist(cliente);
            em.persist(bot);
            em.persist(new Atendente(id, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 1, "Bruno", "bruno@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 2, "Externo", "externo@example.com", outra, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 3, "Admin", "admin@example.com", empresa, Atendente.PerfilAcesso.ADMINISTRADOR));
            Conversa conversa = new Conversa(id, cliente, empresa, bot);
            conversa.adicionarMensagem(1L, "Preciso de ajuda", Mensagem.TipoRemetente.CLIENTE);
            conversa.adicionarMensagem(2L, "Como posso ajudar?", Mensagem.TipoRemetente.CHATBOT);
            em.persist(conversa);
            em.persist(new Ticket(id, "T-" + id, "Suporte", Ticket.TipoSolicitacao.SUPORTE, conversa));
        });
    }

    @Test
    void entraESaiDaFilaPreservandoHistoricoETicket() {
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, service.solicitar(id, id).status());
        tx.executeWithoutResult(s -> assertEquals(1, fila.visualizarFila(em.find(Atendente.class, id)).size()));
        var resultado = service.assumir(id, id);
        assertEquals(id, resultado.responsavelId());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, resultado.status());
        tx.executeWithoutResult(s -> {
            Conversa salva = em.find(Conversa.class, id);
            assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, salva.getStatus());
            assertEquals(id, salva.getResponsavel().getId());
            assertEquals(2, salva.getMensagens().size());
            assertEquals("Preciso de ajuda", salva.getMensagens().getFirst().getConteudo());
            assertNull(salva.getDataEncerramento());
            assertTrue(fila.visualizarFila(em.find(Atendente.class, id)).isEmpty());
            Ticket ticket = em.find(Ticket.class, id);
            assertEquals(Ticket.StatusTicket.PENDENTE, ticket.getStatus());
            assertNull(ticket.getResponsavel());
        });
    }

    @Test
    void bloqueiaRespostaDoBotDesdeAEntradaNaFila() {
        service.solicitar(id, id);
        verificarBotBloqueado();
        service.assumir(id, id);
        verificarBotBloqueado();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "WXN_TESTAR_GROQ_REAL", matches = "true")
    void respostaRealDaGroqEhPersistidaNoHistorico() {
        String resposta = respostaReal.responder(id, id, 3L);
        assertNotNull(resposta);
        assertFalse(resposta.isBlank());
        tx.executeWithoutResult(s -> {
            Conversa salva = em.find(Conversa.class, id);
            assertEquals(3, salva.getMensagens().size());
            assertEquals(Mensagem.TipoRemetente.CHATBOT, salva.getMensagens().getLast().getRemetente());
            assertEquals(resposta, salva.getMensagens().getLast().getConteudo());
        });
    }

    @Test
    void respostaSimuladaEhSalvaETransferenciaDuranteGeracaoDescartaResposta() {
        ProvedorLlm provedor = mock(ProvedorLlm.class);
        var respostas = new RespostaAutomaticaService(provedor, conversas, manager);
        when(provedor.responder(anyList())).thenReturn("Posso ajudar.");
        assertEquals("Posso ajudar.", respostas.responder(id, id, 3L));
        tx.executeWithoutResult(s -> assertEquals(3, em.find(Conversa.class, id).getMensagens().size()));
        when(provedor.responder(anyList())).thenAnswer(invocacao -> {
            service.solicitar(id, id);
            return "Resposta que chegou tarde";
        });
        assertThrows(IllegalStateException.class, () -> respostas.responder(id, id, 4L));
        tx.executeWithoutResult(s -> {
            Conversa salva = em.find(Conversa.class, id);
            assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, salva.getStatus());
            assertEquals(3, salva.getMensagens().size());
        });
    }

    @Test
    void respostaVaziaNaoEhSalva() {
        ProvedorLlm provedor = mock(ProvedorLlm.class);
        when(provedor.responder(anyList())).thenReturn(" ");
        var respostas = new RespostaAutomaticaService(provedor, conversas, manager);
        assertThrows(com.wxn.chatbot.llm.FalhaLlmException.class, () -> respostas.responder(id, id, 3L));
        tx.executeWithoutResult(s -> assertEquals(2, em.find(Conversa.class, id).getMensagens().size()));
    }

    private void verificarBotBloqueado() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s ->
                em.find(Conversa.class, id).adicionarMensagem(3L, "Resposta tardia", Mensagem.TipoRemetente.CHATBOT)));
        tx.executeWithoutResult(s -> assertEquals(2, em.find(Conversa.class, id).getMensagens().size()));
    }

    @Test
    void rejeitaEstadosInvalidos() {
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id));
        service.solicitar(id, id);
        assertThrows(IllegalStateException.class, () -> service.solicitar(id, id));
        service.assumir(id, id);
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id + 1));
        assertThrows(IllegalStateException.class, () -> service.solicitar(id, id));
        tx.executeWithoutResult(s -> em.find(Conversa.class, id).encerrar());
        assertThrows(IllegalStateException.class, () -> service.solicitar(id, id));
        assertThrows(IllegalStateException.class, () -> service.assumir(id, id));
    }

    @Test
    void rejeitaEmpresaExternaAtendenteInativoEInexistente() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(id, id + 1));
        service.solicitar(id, id);
        assertThrows(IllegalArgumentException.class, () -> service.assumir(id, id + 2));
        assertThrows(SecurityException.class, () -> service.assumir(id, Long.MAX_VALUE));
        tx.executeWithoutResult(s -> em.find(Atendente.class, id).desativar(em.find(Atendente.class, id + 3)));
        assertThrows(SecurityException.class, () -> service.assumir(id, id));
        tx.executeWithoutResult(s -> {
            assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, em.find(Conversa.class, id).getStatus());
            assertNull(em.find(Conversa.class, id).getResponsavel());
        });
    }

    @Test
    void rejeitaIdsInvalidosEConversaInexistente() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(null, id));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(id, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(Long.MAX_VALUE, id));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(-1L, id));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(id, null));
        assertThrows(IllegalArgumentException.class, () -> service.assumir(Long.MAX_VALUE, id));
    }

    @Test
    void apenasUmAtendenteAssumeConcorrentemente() throws Exception {
        service.solicitar(id, id);
        CountDownLatch assumiu = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primeiro = executor.submit(() -> tx.executeWithoutResult(s -> {
                service.assumir(id, id);
                assumiu.countDown();
                try {
                    if (!liberar.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo excedido.");
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(erro);
                }
            }));
            assertTrue(assumiu.await(5, TimeUnit.SECONDS));
            Future<?> segundo = executor.submit(() -> service.assumir(id, id + 1));
            assertThrows(TimeoutException.class, () -> segundo.get(300, TimeUnit.MILLISECONDS));
            liberar.countDown();
            primeiro.get(5, TimeUnit.SECONDS);
            ExecutionException erro = assertThrows(ExecutionException.class, () -> segundo.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, erro.getCause());
            tx.executeWithoutResult(s -> assertEquals(id, em.find(Conversa.class, id).getResponsavel().getId()));
        } finally {
            liberar.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
