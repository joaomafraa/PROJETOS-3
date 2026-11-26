package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.NotificacaoChamado;
import com.wxn.chatbot.model.Ticket;

import jakarta.persistence.EntityManager;

// Rotas da HU05.3 com seguranca e banco H2 de verdade.
// Empresa A: Bruno (tem aviso) e Davi (sem aviso). Empresa B: Elisa (tem aviso de outro chamado).
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:notificacoes-chamados-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class NotificacoesChamadosApiTests {

    private static final String URL = "/api/notificacoes-chamados";
    private static final String BRUNO = "bruno@notif-api-a.example";
    private static final String DAVI = "davi@notif-api-a.example";
    private static final String ELISA = "elisa@notif-api-b.example";

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager em;

    private MockMvc mvc;
    private Long avisoDoBruno;
    private Long avisoDaElisa;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Empresa a = new Empresa(81L, "Empresa A");
        Empresa b = new Empresa(82L, "Empresa B");
        em.persist(a);
        em.persist(b);
        Atendente bruno = new Atendente(801L, "Bruno", BRUNO, a, PerfilAcesso.ATENDENTE);
        Atendente davi = new Atendente(802L, "Davi", DAVI, a, PerfilAcesso.ATENDENTE);
        Atendente elisa = new Atendente(811L, "Elisa", ELISA, b, PerfilAcesso.ATENDENTE);
        em.persist(bruno);
        em.persist(davi);
        em.persist(elisa);

        Ticket chamadoA = chamado(8001L, a, "CH-00000000000000008001", "Sem acesso ao portal");
        Ticket chamadoB = chamado(8002L, b, "CH-00000000000000008002", "Boleto não chegou");

        NotificacaoChamado n1 = new NotificacaoChamado(chamadoA, bruno);
        NotificacaoChamado n2 = new NotificacaoChamado(chamadoB, elisa);
        em.persist(n1);
        em.persist(n2);
        em.flush();
        avisoDoBruno = n1.getId();
        avisoDaElisa = n2.getId();
    }

    private Ticket chamado(Long id, Empresa empresa, String protocolo, String descricao) {
        Cliente cliente = new Cliente(id, "+55819999" + id);
        cliente.vincularEmpresa(empresa);
        cliente.alterarNome("Cliente " + id);
        em.persist(cliente);
        Chatbot bot = new Chatbot(id, "Bot", empresa, "Olá");
        em.persist(bot);
        Conversa conversa = new Conversa(id, cliente, empresa, bot);
        em.persist(conversa);
        Ticket ticket = new Ticket(id, protocolo, descricao, Ticket.TipoSolicitacao.SUPORTE, conversa);
        em.persist(ticket);
        return ticket;
    }

    private static RequestPostProcessor como(String email) {
        return user(email).roles("ATENDENTE");
    }

    // ---------- Acesso ----------

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/contagem")).andExpect(status().isUnauthorized());
        // Paginas e scripts do painel redirecionam para o login, como os demais (SecurityHttpTests).
        mvc.perform(get("/notificacoes-chamados.js")).andExpect(status().is3xxRedirection());
    }

    @Test
    void scriptDaTelaCarregaParaQuemEstaLogado() throws Exception {
        mvc.perform(get("/notificacoes-chamados.js").with(como(BRUNO))).andExpect(status().isOk());
    }

    // ---------- Leitura ----------

    @Test
    void atendenteVeSoOsProprios() throws Exception {
        mvc.perform(get(URL).with(como(BRUNO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].protocolo").value("CH-00000000000000008001"))
                .andExpect(jsonPath("$[0].tipoSolicitacao").value("SUPORTE"))
                .andExpect(jsonPath("$[0].clienteNome").value("Cliente 8001"))
                .andExpect(jsonPath("$[0].lida").value(false));

        mvc.perform(get(URL).with(como(DAVI))).andExpect(jsonPath("$", hasSize(0)));

        // Elisa (empresa B) nao ve o chamado da empresa A.
        mvc.perform(get(URL).with(como(ELISA)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].protocolo").value("CH-00000000000000008002"));
    }

    @Test
    void contagemDeNaoLidas() throws Exception {
        mvc.perform(get(URL + "/contagem").with(como(BRUNO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(1));
        mvc.perform(get(URL + "/contagem").with(como(DAVI)))
                .andExpect(jsonPath("$.naoLidas").value(0));
    }

    // ---------- Marcar como lida ----------

    @Test
    void marcarComoLidaExigeCsrf() throws Exception {
        mvc.perform(post(URL + "/" + avisoDoBruno + "/lida").with(como(BRUNO)))
                .andExpect(status().isForbidden());
        assertFalse(em.find(NotificacaoChamado.class, avisoDoBruno).isLida());
    }

    @Test
    void destinatarioMarcaComoLida() throws Exception {
        mvc.perform(post(URL + "/" + avisoDoBruno + "/lida").with(como(BRUNO)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lida").value(true));
        mvc.perform(get(URL + "/contagem").with(como(BRUNO)))
                .andExpect(jsonPath("$.naoLidas").value(0));
        mvc.perform(get(URL).param("somenteNaoLidas", "true").with(como(BRUNO)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ninguemMarcaOAvisoDeOutraPessoa() throws Exception {
        // Mesma empresa, outra pessoa.
        mvc.perform(post(URL + "/" + avisoDoBruno + "/lida").with(como(DAVI)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Notificação não encontrada."));
        // Outra empresa.
        mvc.perform(post(URL + "/" + avisoDoBruno + "/lida").with(como(ELISA)).with(csrf()))
                .andExpect(status().isNotFound());
        // Id que nao existe responde igual.
        mvc.perform(post(URL + "/999999/lida").with(como(BRUNO)).with(csrf()))
                .andExpect(status().isNotFound());

        em.flush();
        em.clear();
        assertFalse(em.find(NotificacaoChamado.class, avisoDoBruno).isLida());
    }

    @Test
    void marcarTodasSoAfetaQuemPediu() throws Exception {
        mvc.perform(post(URL + "/lidas").with(como(BRUNO)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(1));

        em.clear();
        assertTrue(em.find(NotificacaoChamado.class, avisoDoBruno).isLida());
        assertFalse(em.find(NotificacaoChamado.class, avisoDaElisa).isLida());
    }

    @Test
    void idInvalidoDevolve400() throws Exception {
        mvc.perform(post(URL + "/abc/lida").with(como(BRUNO)).with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
