package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.security.ValidacaoClienteService;

class ConsultaSolicitacoesTelegramTests {
    private final IdentificacaoTelegramService identificacao = mock(IdentificacaoTelegramService.class);
    private final RegistrarMensagemTelegramService registro = mock(RegistrarMensagemTelegramService.class);
    private final RespostaTelegramService resposta = mock(RespostaTelegramService.class);
    private final FilaEnvioTelegramService fila = mock(FilaEnvioTelegramService.class);
    private final TelegramCliente telegram = mock(TelegramCliente.class);
    private final ValidacaoClienteService validacao = mock(ValidacaoClienteService.class);
    private final ProcessarMensagemTelegramService processamento = new ProcessarMensagemTelegramService(
            identificacao, registro, resposta, fila, telegram, mock(TransferenciaTelegramService.class),
            validacao, mock(CadastroTelegramService.class));
    private final MensagemTelegram mensagem = new MensagemTelegram(10, 20, 20, "Ana", "/solicitacoes");

    @BeforeEach
    void preparar() {
        when(identificacao.obterConversa(1L, 2L, 20, "Ana")).thenReturn(3L);
        when(registro.registrar(eq(1L), eq(2L), any())).thenReturn(true);
        when(fila.marcarIncerteza(1, 10)).thenReturn(true);
        when(fila.marcarEnviado(1, 10)).thenReturn(true);
    }

    @Test
    void validaComInstrucaoClaraEInformaFalhaDeEnvio() {
        var comando = new MensagemTelegram(10, 20, 20, "Ana", "/validar");
        processamento.processar(1, 2, comando);
        verify(telegram).enviarMensagem(eq(20L), contains("digite os seis números aqui"));
        reset(telegram);
        doThrow(new IllegalStateException("SMTP")).when(validacao).solicitar(1, 3);
        processamento.processar(1, 2, comando);
        verify(telegram).enviarMensagem(eq(20L), startsWith("Não consegui iniciar"));
        verify(telegram, never()).enviarMensagem(eq(20L), contains("SMTP"));
    }

    @Test
    void apresentaProtocoloTipoEEstado() {
        when(validacao.consultarSolicitacoesTelegram(1, 3, 20)).thenReturn(List.of(
                new ValidacaoClienteService.Solicitacao("PROTOCOLO-1", Ticket.TipoSolicitacao.SUPORTE, Ticket.StatusTicket.PENDENTE)));
        assertTrue(processamento.processar(1, 2, mensagem));
        verify(telegram).enviarMensagem(20, "Suas solicitações mais recentes\n\n• PROTOCOLO-1 — Suporte · Pendente");
    }

    @Test
    void distingueListaVaziaDeValidacaoNecessaria() {
        when(validacao.consultarSolicitacoesTelegram(1, 3, 20)).thenReturn(List.of());
        processamento.processar(1, 2, mensagem);
        verify(telegram).enviarMensagem(eq(20L), contains("Você ainda não tem solicitações"));
        reset(telegram);
        when(validacao.consultarSolicitacoesTelegram(1, 3, 20)).thenThrow(new SecurityException("detalhe privado"));
        processamento.processar(1, 2, mensagem);
        verify(telegram).enviarMensagem(eq(20L), contains("cadastro"));
        verify(telegram, never()).enviarMensagem(eq(20L), contains("detalhe privado"));
    }

    @Test
    void falhaApresentaAlternativaSemDetalhesNemResultadoVazio() {
        when(validacao.consultarSolicitacoesTelegram(1, 3, 20)).thenThrow(new IllegalStateException("segredo tecnico"));
        processamento.processar(1, 2, mensagem);
        verify(telegram).enviarMensagem(eq(20L), contains("/atendente"));
        verify(telegram, never()).enviarMensagem(eq(20L), contains("segredo tecnico"));
        verify(telegram, never()).enviarMensagem(eq(20L), contains("Você ainda não tem solicitações"));
        verifyNoInteractions(resposta);
    }
}
