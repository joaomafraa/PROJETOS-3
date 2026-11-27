package com.wxn.chatbot.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.wxn.chatbot.dto.PedidoOrcamentoRegistrado;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.service.PedidoOrcamentoService;

import jakarta.persistence.EntityManager;

// HU06.1 pela API do painel: o contato do orcamento chega no JSON dos detalhes so para a empresa do pedido.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:pedido-orcamento-painel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class PedidoOrcamentoPainelApiTests {

    private static final String ATENDENTE_A = "ana.orcamento@empresa-a.example";
    private static final String ATENDENTE_B = "beto.orcamento@empresa-b.example";
    private static final String TELEFONE_A = "+5581988880001";
    private static final String NECESSIDADE_A = "Orçamento sigiloso da empresa A para 20 licenças.";

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager em;
    @Autowired private PedidoOrcamentoService orcamentos;

    private MockMvc mvc;
    private PedidoOrcamentoRegistrado pedidoA;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Empresa empresaA = new Empresa(6301L, "Empresa A");
        Empresa empresaB = new Empresa(6401L, "Empresa B");
        em.persist(empresaA);
        em.persist(empresaB);
        em.persist(new Atendente(6302L, "Ana A", ATENDENTE_A, empresaA, Atendente.PerfilAcesso.ATENDENTE));
        em.persist(new Atendente(6402L, "Beto B", ATENDENTE_B, empresaB, Atendente.PerfilAcesso.ATENDENTE));
        Chatbot botA = new Chatbot(6303L, "Bot A", empresaA, "Olá");
        Chatbot botB = new Chatbot(6403L, "Bot B", empresaB, "Olá");
        em.persist(botA);
        em.persist(botB);
        Cliente clienteA = new Cliente(6304L, TELEFONE_A);
        clienteA.alterarNome("Marina A");
        clienteA.vincularEmpresa(empresaA);
        em.persist(clienteA);
        em.persist(new Conversa(6305L, clienteA, empresaA, botA));
        em.flush();

        pedidoA = orcamentos.registrar(6301L, 6305L, NECESSIDADE_A, null, "telegram:6301");
        em.flush();
        em.clear();
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void detalhesDaEmpresaDoPedidoMostramNecessidadeEContato() throws Exception {
        mvc.perform(get("/api/tickets/{id}", pedidoA.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ORCAMENTO"))
                .andExpect(jsonPath("$.protocolo", startsWith("PD-")))
                .andExpect(jsonPath("$.descricao").value(NECESSIDADE_A))
                .andExpect(jsonPath("$.contatoOrcamento").value("Telefone: " + TELEFONE_A))
                .andExpect(jsonPath("$.preferenciaHorario").doesNotExist());
    }

    @Test
    @WithMockUser(username = ATENDENTE_A, roles = "ATENDENTE")
    void listaDaEquipeFiltraPorOrcamento() throws Exception {
        mvc.perform(get("/api/tickets").param("tipo", "ORCAMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].protocolo").value(pedidoA.protocolo()));
    }

    @Test
    @WithMockUser(username = ATENDENTE_B, roles = "ATENDENTE")
    void atendenteDeOutraEmpresaNaoVeOPedidoNemOContato() throws Exception {
        mvc.perform(get("/api/tickets/{id}", pedidoA.id()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(TELEFONE_A))))
                .andExpect(content().string(not(containsString("sigiloso"))));

        mvc.perform(get("/api/tickets").param("tipo", "ORCAMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(content().string(not(containsString(pedidoA.protocolo()))));
    }

    @Test
    void semLoginNaoLeOPedidoNemOContato() throws Exception {
        mvc.perform(get("/api/tickets/{id}", pedidoA.id()))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()))
                .andExpect(content().string(not(containsString(TELEFONE_A))));
    }
}
