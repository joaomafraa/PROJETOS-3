package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.ConfiguracaoAtendimento;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:configuracao-atendimento-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ConfiguracaoAtendimentoApiTests {

    private static final String URL = "/api/admin/configuracao-atendimento";
    private static final String ADMIN_A = "ana.admin@config-a.example";
    private static final String ATENDENTE_A = "bruno@config-a.example";
    private static final String ADMIN_B = "davi.admin@config-b.example";

    private static final String CONFIG_VALIDA = """
            {
              "fuso": "America/Recife",
              "mensagemForaExpediente": "Atendemos de segunda a sexta, das 8h às 18h.",
              "horarios": [
                {"diaSemana": "FRIDAY", "abertura": "08:00", "fechamento": "18:00"},
                {"diaSemana": "MONDAY", "abertura": "08:00", "fechamento": "18:00"}
              ],
              "assuntosAutomaticos": ["Boletos", "  ", "boletos", "Horário de funcionamento"]
            }
            """;

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager em;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Empresa a = new Empresa(51L, "Empresa A");
        Empresa b = new Empresa(52L, "Empresa B");
        em.persist(a);
        em.persist(b);
        em.persist(new Atendente(501L, "Ana", ADMIN_A, a, PerfilAcesso.ADMINISTRADOR));
        em.persist(new Atendente(502L, "Bruno", ATENDENTE_A, a, PerfilAcesso.ATENDENTE));
        em.persist(new Atendente(511L, "Davi", ADMIN_B, b, PerfilAcesso.ADMINISTRADOR));
        em.flush();
    }

    private static RequestPostProcessor adminB() {
        return user(ADMIN_B).roles("ADMINISTRADOR");
    }

    private static String comHorario(String abertura, String fechamento, String dia) {
        return """
                {"fuso": "America/Recife", "mensagemForaExpediente": "Fora",
                 "horarios": [{"diaSemana": "%s", "abertura": "%s", "fechamento": "%s"}],
                 "assuntosAutomaticos": []}
                """.formatted(dia, abertura, fechamento);
    }

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void atendenteComumNaoConfigura() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIG_VALIDA))
                .andExpect(status().isForbidden());
        assertNull(em.find(ConfiguracaoAtendimento.class, 51L));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void alteracaoSemCsrfEhRecusada() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CONFIG_VALIDA))
                .andExpect(status().isForbidden());
        assertNull(em.find(ConfiguracaoAtendimento.class, 51L));
    }

    @Test
    void scriptDaTelaExigeLogin() throws Exception {
        mvc.perform(get("/configuracao-atendimento.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/configuracao-atendimento.js").with(user(ATENDENTE_A).roles("ATENDENTE")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void semConfiguracaoMostraOsValoresPadrao() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(false))
                .andExpect(jsonPath("$.fuso").value("America/Recife"))
                .andExpect(jsonPath("$.mensagemForaExpediente").value(ConfiguracaoAtendimento.MENSAGEM_PADRAO))
                .andExpect(jsonPath("$.horarios", hasSize(0)))
                .andExpect(jsonPath("$.equipeDisponivelAgora").value(false));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void salvaELeDeVolta() throws Exception {
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIG_VALIDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(true))
                .andExpect(jsonPath("$.horarios", hasSize(2)))
                // Ordenado por dia da semana, mesmo enviado fora de ordem.
                .andExpect(jsonPath("$.horarios[0].diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$.horarios[0].abertura").value("08:00"))
                .andExpect(jsonPath("$.horarios[1].diaSemana").value("FRIDAY"))
                .andExpect(jsonPath("$.assuntosAutomaticos", hasSize(2)))
                .andExpect(jsonPath("$.assuntosAutomaticos[0]").value("Boletos"));

        em.flush();
        em.clear();

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(true))
                .andExpect(jsonPath("$.mensagemForaExpediente").value("Atendemos de segunda a sexta, das 8h às 18h."))
                .andExpect(jsonPath("$.horarios", hasSize(2)));

        assertEquals(501L, em.find(ConfiguracaoAtendimento.class, 51L).getAtualizadoPor());
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void salvarDeNovoSubstituiOsHorarios() throws Exception {
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIG_VALIDA))
                .andExpect(status().isOk());
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(comHorario("09:00", "12:00", "SATURDAY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(1)))
                .andExpect(jsonPath("$.horarios[0].diaSemana").value("SATURDAY"))
                .andExpect(jsonPath("$.assuntosAutomaticos", hasSize(0)));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void dadosInvalidosDevolvem400ComMensagem() throws Exception {
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(comHorario("18:00", "08:00", "MONDAY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O horário de abertura deve ser antes do fechamento."));

        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(comHorario("8h", "18:00", "MONDAY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Horário de abertura inválido: use o formato HH:mm."));

        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(comHorario("08:00", "18:00", "FERIADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dia da semana inválido: FERIADO"));

        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuso\": \"Lugar/Nenhum\", \"horarios\": [], \"assuntosAutomaticos\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Fuso horário inválido: Lugar/Nenhum"));

        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{ isso nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Os dados enviados estão em um formato inválido."));

        assertNull(em.find(ConfiguracaoAtendimento.class, 51L));
    }

    @Test
    @WithMockUser(username = ADMIN_A, roles = "ADMINISTRADOR")
    void cadaEmpresaVeEAlteraSoASuaConfiguracao() throws Exception {
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CONFIG_VALIDA))
                .andExpect(status().isOk());

        mvc.perform(get(URL).with(adminB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurada").value(false))
                .andExpect(jsonPath("$.horarios", hasSize(0)));

        mvc.perform(put(URL).with(adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(comHorario("10:00", "16:00", "SUNDAY")))
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        assertEquals(2, em.find(ConfiguracaoAtendimento.class, 51L).getHorarios().size());
        assertEquals(1, em.find(ConfiguracaoAtendimento.class, 52L).getHorarios().size());
        assertEquals(511L, em.find(ConfiguracaoAtendimento.class, 52L).getAtualizadoPor());
    }
}
