package com.wxn.chatbot.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.dto.MensagemResumo;
import com.wxn.chatbot.dto.PaginaResumo;
import com.wxn.chatbot.dto.TicketDetalhe;
import com.wxn.chatbot.dto.TicketResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Ticket.StatusTicket;
import com.wxn.chatbot.model.Ticket.TipoSolicitacao;
import com.wxn.chatbot.service.ConsultaTicketsService;
import com.wxn.chatbot.security.ContextoAcesso;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(MockitoExtension.class)
class TicketsControllerTests {
    @Mock private ConsultaTicketsService consulta;
    @Mock private ContextoAcesso contextoAcesso;

    private Atendente atendente;
    private MockMvc mvc;


    @BeforeEach
    void configurar() {
        atendente = new Atendente(
                10L,
                "Ana",
                "ana@example.com",
                new Empresa(1L, "WXN"),
                Atendente.PerfilAcesso.ATENDENTE
        );

        lenient().when(contextoAcesso.atendenteAtual())
                .thenReturn(atendente);

        mvc = MockMvcBuilders.standaloneSetup(
                new PainelTicketsController(
                        consulta,
                        contextoAcesso
                )
        )
        .setControllerAdvice(new TratamentoAcessoNegadoTeste())
        .build();
        }


    @Test
    void repassaFiltrosEPaginacaoAoServico() throws Exception {
        Instant abertura = Instant.parse("2026-11-01T12:00:00Z");
        when(consulta.listar(eq(atendente), any(), eq(1), eq(5))).thenReturn(new PaginaResumo<>(List.of(
                new TicketResumo(7L, "WXN-7", TipoSolicitacao.SUPORTE, StatusTicket.EM_ATENDIMENTO, abertura,
                        3L, "Marina", 10L, "Ana")), 1, 5, 6, 2));

        mvc.perform(get("/api/tickets").param("tipo", "SUPORTE").param("status", "EM_ATENDIMENTO")
                        .param("responsavelId", "10").param("pagina", "1").param("tamanho", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].id").value(7))
                .andExpect(jsonPath("$.conteudo[0].protocolo").value("WXN-7"))
                .andExpect(jsonPath("$.conteudo[0].dataAbertura").value("2026-11-01T12:00:00Z"))
                .andExpect(jsonPath("$.conteudo[0].responsavelNome").value("Ana"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalElementos").value(6))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        verify(consulta).listar(atendente,
                new FiltroTickets(TipoSolicitacao.SUPORTE, StatusTicket.EM_ATENDIMENTO, 10L, false), 1, 5);
    }

    @Test
    void usaPaginaZeroETamanhoVinteQuandoNaoInformados() throws Exception {
        when(consulta.listar(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResumo<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isEmpty());

        verify(consulta).listar(atendente, FiltroTickets.nenhum(), 0, 20);
    }

    @Test
    void responde400ParaParametrosInvalidos() throws Exception {
        when(consulta.listar(any(), any(), eq(-1), anyInt()))
                .thenThrow(new IllegalArgumentException("A pagina deve ser zero ou maior."));

        mvc.perform(get("/api/tickets").param("pagina", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets").param("status", "INVALIDO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets").param("responsavelId", "1").param("semResponsavel", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devolveDetalhesComHistorico() throws Exception {
        Instant abertura = Instant.parse("2026-11-01T12:00:00Z");
        when(consulta.detalhar(7L, atendente)).thenReturn(new TicketDetalhe(7L, "WXN-7", "Sistema parou",
                TipoSolicitacao.SUPORTE, StatusTicket.PENDENTE, abertura, null, null, null,
                new TicketDetalhe.Solicitante(3L, "Marina", null, "+5581999999999", false),
                new TicketDetalhe.ConversaOrigem(4L, Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, abertura, null,
                        "Assistente"),
                List.of(new MensagemResumo(1L, "Meu sistema parou", abertura, Mensagem.TipoRemetente.CLIENTE,
                        null, "Marina"))));

        mvc.perform(get("/api/tickets/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolo").value("WXN-7"))
                .andExpect(jsonPath("$.solicitante.nome").value("Marina"))
                .andExpect(jsonPath("$.conversaOrigem.id").value(4))
                .andExpect(jsonPath("$.historico[0].conteudo").value("Meu sistema parou"));
    }

    @Test
    void responde404ParaTicketInexistenteOuDeOutraEmpresa() throws Exception {
        when(consulta.detalhar(99L, atendente))
                .thenThrow(new IllegalArgumentException("Ticket nao encontrado nesta empresa."));

        mvc.perform(get("/api/tickets/99")).andExpect(status().isNotFound());
    }


    @Test
    void responde403QuandoAtendenteNaoEstaAutorizado()
             throws Exception {

        doThrow(new AccessDeniedException(
                "Atendente nao autenticado."))
                .when(contextoAcesso)
                .atendenteAtual();

        mvc.perform(get("/api/tickets"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/tickets/1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consulta);
     }

        @RestControllerAdvice
        static class TratamentoAcessoNegadoTeste {

                @ExceptionHandler(AccessDeniedException.class)
                public org.springframework.http.ResponseEntity<Void>
                        tratarAcessoNegado(AccessDeniedException ex) {

                return org.springframework.http.ResponseEntity
                        .status(403)
                        .build();
        }
      }


}
