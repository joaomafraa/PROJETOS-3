package com.wxn.chatbot.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:documentos-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Transactional
class DocumentosConhecimentoApiTests {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Test void administraComIsolamentoEValidacao() throws Exception {
        var a = new Empresa(7111L, "A"); var b = new Empresa(7112L, "B"); em.persist(a); em.persist(b);
        em.persist(new Atendente(7111L, "Admin A", "admina@documentos.test", a, Atendente.PerfilAcesso.ADMINISTRADOR));
        em.persist(new Atendente(7112L, "Admin B", "adminb@documentos.test", b, Atendente.PerfilAcesso.ADMINISTRADOR));
        em.persist(new Atendente(7113L, "Pessoa", "pessoa@documentos.test", a, Atendente.PerfilAcesso.ATENDENTE)); em.flush();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/admin/documentos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/documentos").with(user("pessoa@documentos.test").roles("ATENDENTE"))).andExpect(status().isForbidden());
        var resposta = mvc.perform(post("/api/admin/documentos").with(user("admina@documentos.test").roles("ADMINISTRADOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Manual\",\"conteudo\":\"Texto oficial\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versao").value(1)).andReturn();
        long id = new ObjectMapper().readTree(resposta.getResponse().getContentAsString()).get("id").asLong();
        em.flush(); em.clear();
        mvc.perform(get("/api/admin/documentos").with(user("adminb@documentos.test").roles("ADMINISTRADOR"))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(patch("/api/admin/documentos/" + id + "/estado").with(user("adminb@documentos.test").roles("ADMINISTRADOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/documentos/" + id).with(user("admina@documentos.test").roles("ADMINISTRADOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Manual\",\"conteudo\":\"Novo texto\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(2));
        mvc.perform(patch("/api/admin/documentos/" + id + "/estado").with(user("admina@documentos.test").roles("ADMINISTRADOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
    }
}
