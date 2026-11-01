package com.wxn.chatbot.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wxn.chatbot.dto.ConversaResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.FilaConversasService;
import com.wxn.chatbot.service.HistoricoConversasService;

@ExtendWith(MockitoExtension.class)
class FilaControllerTests {
    @Mock private EntityManager entityManager;
    @Mock private ConversaRepository conversas;
    @Mock private HistoricoConversasService historico;
    @Mock private FilaConversasService fila;
    private Atendente atendente;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        atendente = new Atendente(10L, "Ana", "ana@example.com", new Empresa(1L, "WXN"),
                Atendente.PerfilAcesso.ATENDENTE);
        when(entityManager.find(Atendente.class, 10L)).thenReturn(atendente);
        mvc = MockMvcBuilders.standaloneSetup(new PainelHistoricoController(
                entityManager, conversas, historico, fila, 10L)).build();
    }

    @Test
    void delegaAoServicoComAtendenteConfiguradoEPreservaOrdem() throws Exception {
        Instant inicio = Instant.parse("2026-10-01T12:00:00Z");
        when(fila.visualizarFila(atendente)).thenReturn(List.of(
                new ConversaResumo(20L, 1L, "Marina", "+5581999999999", "Bot", inicio,
                        Conversa.Status.AGUARDANDO_ATENDENTE),
                new ConversaResumo(10L, 2L, "Pedro", "+5581888888888", "Bot", inicio.plusSeconds(60),
                        Conversa.Status.AGUARDANDO_ATENDENTE)));

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].dataInicio").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$[1].id").value(10));

        verify(fila).visualizarFila(atendente);
        verifyNoInteractions(conversas, historico);
    }

    @Test
    void traduzRecusaDoServicoPara403SemExporMensagemInterna() throws Exception {
        when(fila.visualizarFila(atendente)).thenThrow(new SecurityException("Detalhe interno"));

        mvc.perform(get("/api/historico/fila"))
                .andExpect(status().isForbidden())
                .andExpect(status().reason("O atendente nao tem permissao para visualizar a fila."));

        verifyNoInteractions(conversas, historico);
    }
}
