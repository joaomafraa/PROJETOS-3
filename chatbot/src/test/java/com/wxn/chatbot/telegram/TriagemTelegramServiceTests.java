package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.repository.ConversaRepository;

class TriagemTelegramServiceTests {
    @Test void classificaEAceitaCorrecaoSemReclassificarMensagemComum() {
        var empresa = new Empresa(1L, "Empresa");
        var cliente = Cliente.semTelefone(1L); cliente.vincularEmpresa(empresa);
        var conversa = new Conversa(1L, cliente, empresa, new Chatbot(1L, "Bot", empresa, "Olá"));
        var repo = mock(ConversaRepository.class); var fila = mock(FilaEnvioTelegramService.class);
        when(repo.buscarParaAtualizar(1L, 1L)).thenReturn(Optional.of(conversa));
        var servico = new TriagemTelegramService(repo, fila);
        var pergunta = new MensagemTelegram(1, 2, 2, "Ana", "Qual o horário?");
        assertTrue(servico.atender(1, 1, pergunta).isEmpty());
        assertEquals(Conversa.Intencao.INDEFINIDA, conversa.getIntencao());
        assertTrue(servico.atender(1, 1, new MensagemTelegram(5, 2, 2, "Ana", "Tenho um problema e quero orçamento")).orElseThrow().contains("/suporte"));
        assertTrue(servico.atender(1, 1, new MensagemTelegram(2, 2, 2, "Ana", "/comercial")).isPresent());
        assertEquals(Conversa.Intencao.COMERCIAL, conversa.getIntencao());
        assertTrue(servico.atender(1, 1, new MensagemTelegram(3, 2, 2, "Ana", "Tenho um problema")).isEmpty());
        assertEquals(Conversa.Intencao.COMERCIAL, conversa.getIntencao());
        servico.atender(1, 1, new MensagemTelegram(4, 2, 2, "Ana", "/suporte"));
        assertEquals(Conversa.Intencao.SUPORTE, conversa.getIntencao());
        verify(fila, times(3)).preparar(eq(1L), anyLong(), eq(2L), anyString());
    }
}
