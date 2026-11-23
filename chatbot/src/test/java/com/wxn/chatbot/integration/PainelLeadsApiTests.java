package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:leads-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class PainelLeadsApiTests {
    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager em;
    private MockMvc mvc;
    private static final String LEAD = """
            {"nome":"Maria","contato":"MARIA@example.com","origem":"SITE","necessidade":"Orçamento"}
            """;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Empresa a = new Empresa(7001L, "Empresa A");
        Empresa b = new Empresa(7002L, "Empresa B");
        em.persist(a);
        em.persist(b);
        em.persist(new Atendente(7001L, "Ana", "ana@leads.test", a, Atendente.PerfilAcesso.ATENDENTE));
        em.persist(new Atendente(7002L, "Bia", "bia@leads.test", b, Atendente.PerfilAcesso.ATENDENTE));
        em.flush();
    }

    @Test
    void registraConsultaEIsolaEmpresaMesmoComParametroForjado() throws Exception {
        mvc.perform(get("/api/leads").with(user("ana@leads.test")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .param("empresaId", "7002").contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.empresaId").value(7001))
                .andExpect(jsonPath("$.contato").value("maria@example.com"));
        em.flush();
        em.clear();
        mvc.perform(get("/api/leads").with(user("ana@leads.test")).param("contato", "MARIA@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].necessidade").value("Orçamento"));
        mvc.perform(get("/api/leads").with(user("bia@leads.test")).param("empresaId", "7001"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void informaDuplicidadeNormalizadaERejeitaDadosInvalidos() throws Exception {
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LEAD)).andExpect(status().isCreated());
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LEAD.replace("Orçamento", "  orçamento  ")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @Test
    void rejeitaCamposInvalidosESemOrigem() throws Exception {
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LEAD.replace("MARIA@example.com", "invalido")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isNotEmpty());
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"contato\":\"maria@example.com\",\"necessidade\":\"Teste\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeAutenticacaoECsrf() throws Exception {
        mvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/leads").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/leads").with(user("ana@leads.test"))
                .contentType(MediaType.APPLICATION_JSON).content(LEAD)).andExpect(status().isForbidden());
    }

    @Test
    void bloqueiaAtendenteInativoMesmoComSessaoExistente() throws Exception {
        Empresa empresa = em.find(Empresa.class, 7001L);
        Atendente admin = new Atendente(7003L, "Admin", "admin@leads.test", empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        em.persist(admin);
        em.find(Atendente.class, 7001L).desativar(admin);
        em.flush();
        mvc.perform(get("/api/leads").with(user("ana@leads.test"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/leads").with(user("ana@leads.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(LEAD)).andExpect(status().isForbidden());
    }
}
