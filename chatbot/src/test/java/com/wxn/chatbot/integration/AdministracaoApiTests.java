package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.security.CredencialAtendente;

import jakarta.persistence.EntityManager;

// Testes das rotas da HU08.3 com seguranca e banco H2 de verdade.
// Empresa A: admin Ana, atendente Bruno e atendente inativa Carla.
// Empresa B: admin Davi e atendente Elisa (servem para testar acesso entre empresas).
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:administracao-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class AdministracaoApiTests {

    private static final String ADMIN_A = "ana.admin@empresa-a.example";
    private static final String ATENDENTE_A = "bruno@empresa-a.example";

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Empresa empresaA = new Empresa(31L, "Empresa A");
        Empresa empresaB = new Empresa(32L, "Empresa B");
        entityManager.persist(empresaA);
        entityManager.persist(empresaB);

        entityManager.persist(new Atendente(301L, "Ana", ADMIN_A, empresaA, PerfilAcesso.ADMINISTRADOR));
        entityManager.persist(new Atendente(302L, "Bruno", ATENDENTE_A, empresaA, PerfilAcesso.ATENDENTE));
        Atendente carla = new Atendente(303L, "Carla", "carla@empresa-a.example", empresaA, PerfilAcesso.ATENDENTE);
        entityManager.persist(carla);
        entityManager.persist(new Atendente(311L, "Davi", "davi.admin@empresa-b.example", empresaB,
                PerfilAcesso.ADMINISTRADOR));
        entityManager.persist(new Atendente(312L, "Elisa", "elisa@empresa-b.example", empresaB,
                PerfilAcesso.ATENDENTE));
        entityManager.flush();

        // Carla fica inativa para os testes de responsavel e de reativacao.
        Atendente ana = entityManager.find(Atendente.class, 301L);
        carla.desativar(ana);
        entityManager.flush();
    }

    // ---------- Acesso ----------

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/admin/atendentes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void atendenteComumNaoEntraNaAdministracao() throws Exception {
        mvc.perform(get("/api/admin/atendentes")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/responsaveis-notificacao")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo\",\"email\":\"novo@empresa-a.example\",\"perfil\":\"ATENDENTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "COORDENADOR")
    void coordenadorTambemNaoEntra() throws Exception {
        mvc.perform(get("/api/admin/atendentes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void alteracaoSemCsrfEhRecusada() throws Exception {
        mvc.perform(post("/api/admin/atendentes/302/desativar")).andExpect(status().isForbidden());
        assertTrue(entityManager.find(Atendente.class, 302L).isAtivo());
    }

    @Test
    void scriptDaTelaExigeLogin() throws Exception {
        mvc.perform(get("/administracao.js")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void scriptDaTelaCarregaParaQuemEstaLogado() throws Exception {
        mvc.perform(get("/administracao.js")).andExpect(status().isOk());
    }

    // ---------- Atendentes ----------

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void listaSomenteAEquipeDaPropriaEmpresa() throws Exception {
        mvc.perform(get("/api/admin/atendentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(content().string(not(containsString("Davi"))))
                .andExpect(content().string(not(containsString("Elisa"))));

        mvc.perform(get("/api/admin/atendentes").param("ativo", "false"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Carla"));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void cadastraAtendenteNaEmpresaDoAdmin() throws Exception {
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fernanda\",\"email\":\"Fernanda@Empresa-A.example\",\"perfil\":\"COORDENADOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Fernanda"))
                .andExpect(jsonPath("$.email").value("fernanda@empresa-a.example"))
                .andExpect(jsonPath("$.perfil").value("COORDENADOR"))
                .andExpect(jsonPath("$.empresaId").value(31))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void emailRepetidoNaMesmaEmpresaDevolve409() throws Exception {
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro Bruno\",\"email\":\"" + ATENDENTE_A + "\",\"perfil\":\"ATENDENTE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Já existe um atendente com este e-mail na empresa."));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void dadosInvalidosNoCadastroDevolvem400() throws Exception {
        // e-mail sem formato
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Gil\",\"email\":\"gil-sem-arroba\",\"perfil\":\"ATENDENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").isString());
        // nome em branco
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \",\"email\":\"gil@empresa-a.example\",\"perfil\":\"ATENDENTE\"}"))
                .andExpect(status().isBadRequest());
        // sem perfil
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Gil\",\"email\":\"gil@empresa-a.example\"}"))
                .andExpect(status().isBadRequest());
        // perfil que nao existe
        mvc.perform(post("/api/admin/atendentes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Gil\",\"email\":\"gil@empresa-a.example\",\"perfil\":\"CHEFE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Os dados enviados estão em um formato inválido."));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void alteraPerfilDesativaEAtiva() throws Exception {
        mvc.perform(patch("/api/admin/atendentes/302/perfil").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"COORDENADOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("COORDENADOR"));

        mvc.perform(post("/api/admin/atendentes/302/desativar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(post("/api/admin/atendentes/302/ativar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void ativarQuemJaEstaAtivoDevolve409() throws Exception {
        mvc.perform(post("/api/admin/atendentes/302/ativar").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("O atendente já está ativo."));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void adminNaoMexeNoProprioPerfilNemSeDesativa() throws Exception {
        mvc.perform(patch("/api/admin/atendentes/301/perfil").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"ATENDENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Um administrador não pode alterar o próprio perfil."));

        mvc.perform(post("/api/admin/atendentes/301/desativar").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Um administrador não pode desativar a si mesmo."));

        assertEquals(PerfilAcesso.ADMINISTRADOR, entityManager.find(Atendente.class, 301L).getPerfil());
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void atendenteDeOutraEmpresaPareceQueNaoExiste() throws Exception {
        // 312 = Elisa, da empresa B. A resposta e igual a de um id inexistente (999).
        mvc.perform(get("/api/admin/atendentes/312")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/atendentes/999")).andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/atendentes/312/desativar").with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/atendentes/312/perfil").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/atendentes/312/senha").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"SenhaNova123\"}"))
                .andExpect(status().isNotFound());

        entityManager.clear();
        Atendente elisa = entityManager.find(Atendente.class, 312L);
        assertTrue(elisa.isAtivo());
        assertEquals(PerfilAcesso.ATENDENTE, elisa.getPerfil());
        assertNull(entityManager.find(CredencialAtendente.class, 312L));
    }

    // ---------- Senha ----------

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void defineSenhaSemDevolverASenha() throws Exception {
        mvc.perform(put("/api/admin/atendentes/302/senha").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"SenhaNova123\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        CredencialAtendente credencial = entityManager.find(CredencialAtendente.class, 302L);
        assertNotNull(credencial);
        // O banco guarda o hash, nunca o texto digitado.
        assertTrue(!credencial.getSenhaHash().equals("SenhaNova123"));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void senhaCurtaDevolve400() throws Exception {
        mvc.perform(put("/api/admin/atendentes/302/senha").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A senha deve ter pelo menos 8 caracteres."));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void respostasNaoTrazemSenhaNemHash() throws Exception {
        mvc.perform(get("/api/admin/atendentes"))
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("hash"))));
    }

    // ---------- Responsaveis por notificacao ----------

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void defineEListaResponsaveis() throws Exception {
        mvc.perform(put("/api/admin/responsaveis-notificacao/NOVO_LEAD").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"atendenteIds\":[301,302]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/admin/responsaveis-notificacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("NOVO_LEAD"));

        // Lista vazia tira todo mundo daquele tipo.
        mvc.perform(put("/api/admin/responsaveis-notificacao/NOVO_LEAD").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"atendenteIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void responsavelInativoEhRecusado() throws Exception {
        mvc.perform(put("/api/admin/responsaveis-notificacao/NOVO_TICKET").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"atendenteIds\":[303]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem")
                        .value("Um atendente inativo não pode ser responsável por notificações."));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void responsavelDeOutraEmpresaEhRecusado() throws Exception {
        mvc.perform(put("/api/admin/responsaveis-notificacao/NOVO_TICKET").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"atendenteIds\":[302,312]}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/responsaveis-notificacao"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void tipoDeNotificacaoInexistenteDevolve400() throws Exception {
        mvc.perform(put("/api/admin/responsaveis-notificacao/QUALQUER_COISA").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"atendenteIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
