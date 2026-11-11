package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fila-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles({"test", "painel-local"})
@Transactional
class FilaApiTests {
    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private Empresa empresa;
    private Atendente atendente;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        empresa = new Empresa(1L, "WXN");
        entityManager.persist(empresa);
        atendente = new Atendente(10L, "Ana", "ana@example.com", empresa,
                Atendente.PerfilAcesso.ATENDENTE);
        entityManager.persist(atendente);
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void retornaContratoJsonComDadosPersistidos() throws Exception {
        Conversa conversa = criarConversa(1L, empresa, "Marina", Conversa.Status.AGUARDANDO_ATENDENTE);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.aMapWithSize(7)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].clienteId").value(1))
                .andExpect(jsonPath("$[0].clienteNome").value("Marina"))
                .andExpect(jsonPath("$[0].numeroTelefone").value("+5581999999999"))
                .andExpect(jsonPath("$[0].chatbotNome").value("Bot 1"))
                .andExpect(jsonPath("$[0].dataInicio").isString())
                .andExpect(jsonPath("$[0].status").value(conversa.getStatus().name()));
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void retornaArrayVazioQuandoNaoHaConversasAguardando() throws Exception {
        criarConversa(1L, empresa, "Marina", Conversa.Status.EM_ATENDIMENTO_AUTOMATICO);

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void filtraEmpresaEStatusMesmoComParametrosEnviadosPeloCliente() throws Exception {
        Empresa outraEmpresa = new Empresa(2L, "Outra empresa");
        entityManager.persist(outraEmpresa);
        entityManager.persist(new Atendente(20L, "Externo", "externo@example.com", outraEmpresa,
                Atendente.PerfilAcesso.ATENDENTE));
        criarConversa(1L, empresa, "Marina", Conversa.Status.AGUARDANDO_ATENDENTE);
        criarConversa(2L, outraEmpresa, "Externo", Conversa.Status.AGUARDANDO_ATENDENTE);
        criarConversa(3L, empresa, "Automatico", Conversa.Status.EM_ATENDIMENTO_AUTOMATICO);
        criarConversa(4L, empresa, "Humano", Conversa.Status.EM_ATENDIMENTO_HUMANO);
        criarConversa(5L, empresa, "Encerrada", Conversa.Status.ENCERRADA);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/historico/fila")
                        .param("empresaId", "2").param("atendenteId", "20")
                        .param("status", "ENCERRADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void preservaNomeAusenteSemInventarDados() throws Exception {
        criarConversa(1L, empresa, null, Conversa.Status.AGUARDANDO_ATENDENTE);

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clienteNome", nullValue()));
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void rejeitaAtendenteInativoMesmoQuandoClienteIndicaOutro() throws Exception {
        Atendente admin = new Atendente(11L, "Admin", "admin@example.com", empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        entityManager.persist(admin);
        atendente.desativar(admin);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/historico/fila").param("atendenteId", "11"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ana@example.com", roles = "ATENDENTE")
    void rejeitaAtendenteConfiguradoInexistente() throws Exception {
        entityManager.remove(atendente);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isForbidden());
    }

    private Conversa criarConversa(Long id, Empresa dona, String nome, Conversa.Status status) {
        Cliente cliente = new Cliente(id, "+55819999" + (100000 - id));
        if (nome != null) {
            cliente.alterarNome(nome);
        }
        cliente.vincularEmpresa(dona);
        Chatbot bot = new Chatbot(id, "Bot " + id, dona, "Ola");
        entityManager.persist(cliente);
        entityManager.persist(bot);
        Conversa conversa = new Conversa(id, cliente, dona, bot);
        if (status == Conversa.Status.AGUARDANDO_ATENDENTE
                || status == Conversa.Status.EM_ATENDIMENTO_HUMANO) {
            conversa.solicitarAtendimentoHumano();
        }
        if (status == Conversa.Status.EM_ATENDIMENTO_HUMANO) {
            conversa.iniciarAtendimentoHumano(atendente);
        }
        if (status == Conversa.Status.ENCERRADA) {
            conversa.encerrar();
        }
        entityManager.persist(conversa);
        return conversa;
    }
}
