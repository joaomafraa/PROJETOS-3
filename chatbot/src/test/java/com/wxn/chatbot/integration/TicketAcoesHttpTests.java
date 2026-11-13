package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.TicketRepository;
import com.wxn.chatbot.telegram.TelegramCliente;

import jakarta.persistence.EntityManager;

// Fluxo completo pela API: seguranca, CSRF, identidade da sessao, banco e regras de negocio.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:acoes-ticket;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketAcoesHttpTests {
    private static final AtomicLong IDS = new AtomicLong(5000);

    // O canal do Telegram nao participa deste fluxo; evita criar o cliente HTTP real.
    @MockitoBean private TelegramCliente telegramCliente;

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private TicketRepository tickets;
    @Autowired private PlatformTransactionManager manager;

    private long id;
    private String ana;
    private String bruno;
    private String externo;
    private String inativo;

    @BeforeEach
    void preparar() {
        id = IDS.getAndAdd(10);
        ana = "ana" + id + "@example.com";
        bruno = "bruno" + id + "@example.com";
        externo = "externo" + id + "@example.com";
        inativo = "inativo" + id + "@example.com";
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Empresa empresa = new Empresa(id, "WXN");
            Empresa outra = new Empresa(id + 1, "Outra");
            em.persist(empresa);
            em.persist(outra);
            Cliente cliente = new Cliente(id, "+5581999999999");
            cliente.vincularEmpresa(empresa);
            em.persist(cliente);
            Chatbot bot = new Chatbot(id, "Assistente", empresa, "Olá");
            em.persist(bot);
            em.persist(new Atendente(id, "Ana", ana, empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 1, "Bruno", bruno, empresa, Atendente.PerfilAcesso.ATENDENTE));
            em.persist(new Atendente(id + 2, "Externo", externo, outra, Atendente.PerfilAcesso.ATENDENTE));
            Atendente admin = new Atendente(id + 3, "Admin", "admin" + id + "@example.com", empresa,
                    Atendente.PerfilAcesso.ADMINISTRADOR);
            em.persist(admin);
            Atendente desativado = new Atendente(id + 4, "Inativo", inativo, empresa, Atendente.PerfilAcesso.ATENDENTE);
            em.persist(desativado);
            desativado.desativar(admin);
            Conversa conversa = new Conversa(id, cliente, empresa, bot);
            em.persist(conversa);
            em.persist(new Ticket(id, "WXN-" + id, "Suporte", Ticket.TipoSolicitacao.SUPORTE, conversa));
        });
    }

    private Ticket recarregar() {
        return new TransactionTemplate(manager).execute(status -> {
            Ticket ticket = tickets.findByIdAndEmpresaId(id, id).orElseThrow();
            ticket.getResponsavel(); // inicializa dentro da transacao
            return ticket;
        });
    }

    private org.springframework.test.web.servlet.ResultActions executar(String acao, String email) throws Exception {
        var requisicao = post("/api/tickets/" + id + "/" + acao).with(csrf());
        if (email != null) requisicao = requisicao.with(user(email));
        return mvc.perform(requisicao);
    }

    @Test
    void exigeAutenticacaoECsrf() throws Exception {
        executar("assumir", null).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tickets/" + id + "/assumir").with(user(ana))).andExpect(status().isForbidden());
        assertEquals(Ticket.StatusTicket.PENDENTE, recarregar().getStatus());
    }

    @Test
    void atendenteAssumeEConcluiEOEstadoPersiste() throws Exception {
        executar("assumir", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.responsavelId").value(id));
        Ticket emAtendimento = recarregar();
        assertEquals(Ticket.StatusTicket.EM_ATENDIMENTO, emAtendimento.getStatus());
        assertEquals("Ana", emAtendimento.getResponsavel().getNome());

        executar("concluir", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
        Ticket concluido = recarregar();
        assertEquals(Ticket.StatusTicket.CONCLUIDO, concluido.getStatus());
        assertNotNull(concluido.getDataConclusao());
    }

    @Test
    void rejeitaTicketJaAssumidoEConclusaoPorOutroAtendente() throws Exception {
        executar("assumir", ana).andExpect(status().isOk());

        executar("assumir", bruno).andExpect(status().isConflict());
        executar("concluir", bruno).andExpect(status().isForbidden());
        assertEquals("Ana", recarregar().getResponsavel().getNome());
        assertEquals(Ticket.StatusTicket.EM_ATENDIMENTO, recarregar().getStatus());
    }

    @Test
    void naoConcluiTicketPendenteNemJaConcluido() throws Exception {
        executar("concluir", ana).andExpect(status().isConflict());
        executar("assumir", ana).andExpect(status().isOk());
        executar("concluir", ana).andExpect(status().isOk());
        executar("concluir", ana).andExpect(status().isConflict());
        executar("assumir", ana).andExpect(status().isConflict());
    }

    @Test
    void atendenteDeOutraEmpresaNaoEnxergaNemAltera() throws Exception {
        executar("assumir", externo).andExpect(status().isNotFound());
        executar("concluir", externo).andExpect(status().isNotFound());
        Ticket ticket = recarregar();
        assertEquals(Ticket.StatusTicket.PENDENTE, ticket.getStatus());
        assertNull(ticket.getResponsavel());
    }

    @Test
    void atendenteInativoNaoAltera() throws Exception {
        executar("assumir", inativo).andExpect(status().isForbidden());
        assertEquals(Ticket.StatusTicket.PENDENTE, recarregar().getStatus());
    }

    @Test
    void ticketInexistenteRetorna404() throws Exception {
        mvc.perform(post("/api/tickets/987654321/assumir").with(csrf()).with(user(ana)))
                .andExpect(status().isNotFound());
    }

    @Test
    void doisAtendentesSimultaneosApenasUmVence() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (String email : List.of(ana, bruno)) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    return executar("assumir", email).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> resultado : resultados) codigos.add(resultado.get());
            assertEquals(1, codigos.stream().filter(codigo -> codigo == 200).count(), codigos.toString());
            assertEquals(1, codigos.stream().filter(codigo -> codigo == 409).count(), codigos.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(Ticket.StatusTicket.EM_ATENDIMENTO, recarregar().getStatus());
    }
}
