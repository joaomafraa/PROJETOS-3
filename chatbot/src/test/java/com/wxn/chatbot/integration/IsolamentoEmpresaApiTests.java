package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Lead;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Ticket;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:isolamento-empresa-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class IsolamentoEmpresaApiTests {

    private static final String ATENDENTE_A = "ana.isolamento@empresa-a.example";
    private static final long EMPRESA_A = 6101L;
    private static final long EMPRESA_B = 6201L;
    private static final long CONVERSA_A = 6103L;
    private static final long CONVERSA_B = 6203L;
    private static final long TICKET_B = 6204L;

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Empresa empresaA = new Empresa(EMPRESA_A, "Empresa A");
        Empresa empresaB = new Empresa(EMPRESA_B, "Empresa B");
        entityManager.persist(empresaA);
        entityManager.persist(empresaB);

        Atendente atendenteA = new Atendente(6102L, "Ana A", ATENDENTE_A, empresaA,
                Atendente.PerfilAcesso.ATENDENTE);
        Atendente atendenteB = new Atendente(6202L, "Beto B", "beto.isolamento@empresa-b.example", empresaB,
                Atendente.PerfilAcesso.ATENDENTE);
        entityManager.persist(atendenteA);
        entityManager.persist(atendenteB);

        Conversa conversaA = criarConversa(CONVERSA_A, empresaA, "Cliente A", "segredo-a", 6105L);
        Conversa conversaB = criarConversa(CONVERSA_B, empresaB, "Cliente B", "segredo-b", 6205L);
        entityManager.persist(conversaA);
        entityManager.persist(conversaB);

        entityManager.persist(new Ticket(6104L, "A-6104", "Solicitação A",
                Ticket.TipoSolicitacao.SUPORTE, conversaA));
        entityManager.persist(new Ticket(TICKET_B, "B-6204", "Solicitação sigilosa B",
                Ticket.TipoSolicitacao.SUPORTE, conversaB));

        entityManager.persist(new Lead(empresaA, "Lead A", "lead-a@example.com",
                Lead.Origem.SITE, "Necessidade A"));
        entityManager.persist(new Lead(empresaB, "Lead B sigiloso", "lead-b@example.com",
                Lead.Origem.SITE, "Necessidade B sigilosa"));

        entityManager.createNativeQuery("""
                insert into telegram_envios_atendente
                    (empresa_id, conversa_id, mensagem_id, chat_id, conteudo, status)
                values (:empresa, :conversa, 1, :chat, :conteudo, 'INCERTO')
                """)
                .setParameter("empresa", EMPRESA_A)
                .setParameter("conversa", CONVERSA_A)
                .setParameter("chat", 6106L)
                .setParameter("conteudo", "envio-a")
                .executeUpdate();
        entityManager.createNativeQuery("""
                insert into telegram_envios_atendente
                    (empresa_id, conversa_id, mensagem_id, chat_id, conteudo, status)
                values (:empresa, :conversa, 1, :chat, :conteudo, 'INCERTO')
                """)
                .setParameter("empresa", EMPRESA_B)
                .setParameter("conversa", CONVERSA_B)
                .setParameter("chat", 6206L)
                .setParameter("conteudo", "envio-b-sigiloso")
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void listasUsamSomenteAEmpresaDaSessao() throws Exception {
        mvc.perform(get("/api/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(CONVERSA_A))
                .andExpect(content().string(not(containsString("Cliente B"))));

        mvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].protocolo").value("A-6104"))
                .andExpect(content().string(not(containsString("B-6204"))));

        mvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Lead A"))
                .andExpect(content().string(not(containsString("Lead B sigiloso"))));

        mvc.perform(get("/api/conversas/{id}/envios", CONVERSA_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mensagemId").value(1));

        mvc.perform(get("/api/conversas/{id}/envios", CONVERSA_B))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void identificadoresDeOutraEmpresaNaoExibemDetalhes() throws Exception {
        mvc.perform(get("/api/historico/{id}/mensagens", CONVERSA_B))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("segredo-b"))));

        mvc.perform(get("/api/tickets/{id}", TICKET_B))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Solicitação sigilosa B"))));
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void naoAssumeConversaDeOutraEmpresa() throws Exception {
        mvc.perform(post("/api/conversas/{id}/assumir", CONVERSA_B).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Empresa B"))));

        entityManager.clear();
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE,
                entityManager.find(Conversa.class, CONVERSA_B).getStatus());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void naoEnviaMensagemParaConversaDeOutraEmpresa() throws Exception {
        mvc.perform(post("/api/conversas/{id}/mensagens", CONVERSA_B).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"tentativa entre empresas\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Empresa B"))));

        Number quantidade = (Number) entityManager.createNativeQuery("""
                select count(*) from telegram_envios_atendente
                 where empresa_id = :empresa and conversa_id = :conversa
                """)
                .setParameter("empresa", EMPRESA_B)
                .setParameter("conversa", CONVERSA_B)
                .getSingleResult();
        assertEquals(1L, quantidade.longValue());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void naoRevisaEnvioDeOutraEmpresa() throws Exception {
        mvc.perform(post("/api/conversas/{id}/envios/1/confirmar", CONVERSA_B).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Empresa B"))));

        String statusEnvio = (String) entityManager.createNativeQuery("""
                select status from telegram_envios_atendente
                 where empresa_id = :empresa and conversa_id = :conversa and mensagem_id = 1
                """)
                .setParameter("empresa", EMPRESA_B)
                .setParameter("conversa", CONVERSA_B)
                .getSingleResult();
        assertEquals("INCERTO", statusEnvio);
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void naoReenviaMensagemDeOutraEmpresa() throws Exception {
        mvc.perform(post("/api/conversas/{id}/envios/1/reenviar", CONVERSA_B).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Empresa B"))));

        String statusEnvio = (String) entityManager.createNativeQuery("""
                select status from telegram_envios_atendente
                 where empresa_id = :empresa and conversa_id = :conversa and mensagem_id = 1
                """)
                .setParameter("empresa", EMPRESA_B)
                .setParameter("conversa", CONVERSA_B)
                .getSingleResult();
        assertEquals("INCERTO", statusEnvio);
    }

    private Conversa criarConversa(long id, Empresa empresa, String nomeCliente,
            String conteudo, long idBase) {
        Cliente cliente = new Cliente(idBase, "+55819" + idBase);
        cliente.alterarNome(nomeCliente);
        cliente.vincularEmpresa(empresa);
        entityManager.persist(cliente);

        Chatbot chatbot = new Chatbot(idBase + 1, "Bot " + empresa.getNome(), empresa, "Olá");
        entityManager.persist(chatbot);

        Conversa conversa = new Conversa(id, cliente, empresa, chatbot);
        conversa.adicionarMensagem(1L, conteudo, Mensagem.TipoRemetente.CLIENTE);
        conversa.solicitarAtendimentoHumano();
        return conversa;
    }
}
