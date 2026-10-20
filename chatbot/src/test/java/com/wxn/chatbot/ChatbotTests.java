package com.wxn.chatbot;
 
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
 
import org.junit.jupiter.api.Test;
 
class ChatbotTests {
 
    private final Empresa wxn = new Empresa(1L, "WXN");
    private final Empresa outraEmpresa = new Empresa(2L, "Outra");
    private final Atendente admin = new Atendente(1L, "Ana", "ana@wxn.com", wxn, Atendente.PerfilAcesso.ADMINISTRADOR);
 
    @Test
    void criaChatbotAtivoComDadosValidos() {
        Chatbot chatbot = novoChatbot();
 
        assertAll(
                () -> assertEquals(1L, chatbot.getId()),
                () -> assertEquals("Assistente WXN", chatbot.getNome()),
                () -> assertSame(wxn, chatbot.getEmpresa()),
                () -> assertEquals("Olá! Como posso ajudar?", chatbot.getMensagemBoasVindas()),
                () -> assertTrue(chatbot.isAtivo()));
    }
 
    @Test
    void removeEspacosDasPontasDoNomeEDaMensagem() {
        Chatbot chatbot = new Chatbot(1L, "  Assistente WXN ", wxn, "  Olá!  ");
 
        assertEquals("Assistente WXN", chatbot.getNome());
        assertEquals("Olá!", chatbot.getMensagemBoasVindas());
    }
 
    @Test
    void rejeitaDadosInvalidosNaCriacao() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Chatbot(null, "Bot", wxn, "Olá")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Chatbot(0L, "Bot", wxn, "Olá")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Chatbot(-1L, "Bot", wxn, "Olá")),
                () -> assertThrows(NullPointerException.class, () -> new Chatbot(1L, null, wxn, "Olá")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Chatbot(1L, "  ", wxn, "Olá")),
                () -> assertThrows(NullPointerException.class, () -> new Chatbot(1L, "Bot", null, "Olá")),
                () -> assertThrows(NullPointerException.class, () -> new Chatbot(1L, "Bot", wxn, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Chatbot(1L, "Bot", wxn, " \t\n")));
    }
}