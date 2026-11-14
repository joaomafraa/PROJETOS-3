package com.wxn.chatbot.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wxn.chatbot.dto.TicketAssumido;
import com.wxn.chatbot.dto.TicketConcluido;
import com.wxn.chatbot.model.Ticket.StatusTicket;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.AssumirTicketService;
import com.wxn.chatbot.service.ConcluirTicketService;

@ExtendWith(MockitoExtension.class)
class TicketAcoesControllerTests {
    @Mock private AssumirTicketService assumirTicket;
    @Mock private ConcluirTicketService concluirTicket;
    @Mock private ContextoAcesso contextoAcesso;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(
                new TicketAcoesController(assumirTicket, concluirTicket, contextoAcesso)).build();
    }

    @Test
    void assumeUsandoSomenteOAtendenteAutenticado() throws Exception {
        when(contextoAcesso.atendenteIdAtual()).thenReturn(10L);
        when(assumirTicket.assumir(7L, 10L))
                .thenReturn(new TicketAssumido(7L, "WXN-7", StatusTicket.EM_ATENDIMENTO, 10L));

        mvc.perform(post("/api/tickets/7/assumir").param("atendenteId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.responsavelId").value(10));
        verify(assumirTicket).assumir(7L, 10L);
        verify(assumirTicket, never()).assumir(anyLong(), eq(99L));
    }

    @Test
    void conclueComOAtendenteAutenticado() throws Exception {
        Instant fim = Instant.parse("2026-11-12T10:00:00Z");
        when(contextoAcesso.atendenteIdAtual()).thenReturn(10L);
        when(concluirTicket.concluir(7L, 10L))
                .thenReturn(new TicketConcluido(7L, "WXN-7", StatusTicket.CONCLUIDO, 10L, fim));

        mvc.perform(post("/api/tickets/7/concluir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.dataConclusao").value("2026-11-12T10:00:00Z"));
    }

    @Test
    void ticketInexistenteOuDeOutraEmpresaRetorna404() throws Exception {
        when(contextoAcesso.atendenteIdAtual()).thenReturn(10L);
        when(assumirTicket.assumir(8L, 10L)).thenThrow(new IllegalArgumentException("Ticket não encontrado nesta empresa."));
        when(concluirTicket.concluir(8L, 10L)).thenThrow(new IllegalArgumentException("Ticket não encontrado nesta empresa."));

        mvc.perform(post("/api/tickets/8/assumir")).andExpect(status().isNotFound());
        mvc.perform(post("/api/tickets/8/concluir")).andExpect(status().isNotFound());
    }

    @Test
    void estadoIncompativelRetorna409() throws Exception {
        when(contextoAcesso.atendenteIdAtual()).thenReturn(10L);
        when(assumirTicket.assumir(7L, 10L)).thenThrow(new IllegalStateException("Somente um ticket pendente pode ser assumido."));
        when(concluirTicket.concluir(7L, 10L)).thenThrow(new IllegalStateException("Somente um ticket em atendimento pode ser concluído."));

        mvc.perform(post("/api/tickets/7/assumir")).andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/7/concluir")).andExpect(status().isConflict());
    }

    @Test
    void semPermissaoRetorna403() throws Exception {
        when(contextoAcesso.atendenteIdAtual()).thenReturn(10L);
        when(assumirTicket.assumir(7L, 10L)).thenThrow(new SecurityException("É necessário um atendente cadastrado e ativo."));
        when(concluirTicket.concluir(7L, 10L)).thenThrow(new SecurityException("Somente o responsável pelo ticket pode concluí-lo."));

        mvc.perform(post("/api/tickets/7/assumir")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/7/concluir")).andExpect(status().isForbidden());
    }

    @Test
    void semAtendenteAutenticadoNaoChamaOsServicos() throws Exception {
        when(contextoAcesso.atendenteIdAtual()).thenThrow(new AccessDeniedException("Atendente nao autenticado."));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> mvc.perform(post("/api/tickets/7/assumir")));
        verifyNoInteractions(assumirTicket, concluirTicket);
    }
}
