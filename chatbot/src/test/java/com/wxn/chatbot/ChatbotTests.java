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

    @Test
    void identificaEmpresaPeloId() {
        Chatbot chatbot = novoChatbot();
 
        assertTrue(chatbot.pertenceA(new Empresa(1L, "WXN (outra instância)")));
        assertFalse(chatbot.pertenceA(outraEmpresa));
        assertFalse(chatbot.pertenceA(null));
    }
 
    @Test
    void verificaSePodeAtenderNaEmpresa() {
        Chatbot chatbot = novoChatbot();
 
        assertDoesNotThrow(() -> chatbot.verificarPodeAtender(wxn));
        assertThrows(IllegalArgumentException.class, () -> chatbot.verificarPodeAtender(outraEmpresa));
        assertThrows(NullPointerException.class, () -> chatbot.verificarPodeAtender(null));
 
        chatbot.desativar(admin);
        assertThrows(IllegalStateException.class, () -> chatbot.verificarPodeAtender(wxn));
    }
 
    @Test
    void administradorDaMesmaEmpresaConfiguraOChatbot() {
        Chatbot chatbot = novoChatbot();
 
        chatbot.alterarNome("Atendimento WXN", admin);
        chatbot.alterarMensagemBoasVindas("Bem-vindo à WXN!", admin);
        assertEquals("Atendimento WXN", chatbot.getNome());
        assertEquals("Bem-vindo à WXN!", chatbot.getMensagemBoasVindas());
 
        chatbot.desativar(admin);
        assertFalse(chatbot.isAtivo());
        assertThrows(IllegalStateException.class, () -> chatbot.desativar(admin));
 
        chatbot.ativar(admin);
        assertTrue(chatbot.isAtivo());
        assertThrows(IllegalStateException.class, () -> chatbot.ativar(admin));
    }
 
    @Test
    void rejeitaValoresInvalidosSemAlterarConfiguracao() {
        Chatbot chatbot = novoChatbot();
 
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> chatbot.alterarNome("", admin)),
                () -> assertThrows(NullPointerException.class, () -> chatbot.alterarNome(null, admin)),
                () -> assertThrows(IllegalArgumentException.class, () -> chatbot.alterarMensagemBoasVindas("  ", admin)),
                () -> assertThrows(NullPointerException.class, () -> chatbot.alterarMensagemBoasVindas(null, admin)));
 
        assertEquals("Assistente WXN", chatbot.getNome());
        assertEquals("Olá! Como posso ajudar?", chatbot.getMensagemBoasVindas());
    }
 
    @Test
    void rejeitaSolicitanteSemPermissaoSemAlterarEstado() {
        Chatbot chatbot = novoChatbot();
        Atendente atendente = new Atendente(2L, "Bruno", "bruno@wxn.com", wxn, Atendente.PerfilAcesso.ATENDENTE);
        Atendente coordenador = new Atendente(3L, "Caio", "caio@wxn.com", wxn, Atendente.PerfilAcesso.COORDENADOR);
        Atendente adminDeOutraEmpresa = new Atendente(4L, "Dora", "dora@outra.com", outraEmpresa,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente adminInativo = new Atendente(5L, "Edu", "edu@wxn.com", wxn, Atendente.PerfilAcesso.ADMINISTRADOR);
        adminInativo.desativar(admin);
 
        for (Atendente solicitante : new Atendente[] { atendente, coordenador, adminDeOutraEmpresa, adminInativo }) {
            assertThrows(SecurityException.class, () -> chatbot.alterarNome("Outro nome", solicitante));
            assertThrows(SecurityException.class, () -> chatbot.alterarMensagemBoasVindas("Outra mensagem", solicitante));
            assertThrows(SecurityException.class, () -> chatbot.desativar(solicitante));
        }
        assertThrows(NullPointerException.class, () -> chatbot.desativar(null));
 
        assertEquals("Assistente WXN", chatbot.getNome());
        assertEquals("Olá! Como posso ajudar?", chatbot.getMensagemBoasVindas());
        assertTrue(chatbot.isAtivo());
    }
 
    private Chatbot novoChatbot() {
        return new Chatbot(1L, "Assistente WXN", wxn, "Olá! Como posso ajudar?");
    }
}