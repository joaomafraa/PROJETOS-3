package com.wxn.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MensagemTests {
    private final Empresa empresa = new Empresa(1L, "WXN");
    private final Chatbot chatbot = new Chatbot(1L, "Bot", empresa, "Ola");

    @Test
    void criaMensagemDeClienteComDadosValidos() {
        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        Mensagem mensagem = new Mensagem(
                1L,
                conversa,
                "Preciso de ajuda",
                Mensagem.TipoRemetente.CLIENTE);

        assertEquals(1L, mensagem.getId());
        assertSame(conversa, mensagem.getConversa());
        assertEquals("Preciso de ajuda", mensagem.getConteudo());
        assertEquals(Mensagem.TipoRemetente.CLIENTE, mensagem.getRemetente());
        assertNotNull(mensagem.getDataEnvio());
        assertNull(mensagem.getAtendente());
    }

    @Test
    void criaMensagemDeChatbotSemAtendente() {
        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        Mensagem mensagem = new Mensagem(
                2L,
                conversa,
                "Como posso ajudar?",
                Mensagem.TipoRemetente.CHATBOT);

        assertEquals(Mensagem.TipoRemetente.CHATBOT, mensagem.getRemetente());
        assertNull(mensagem.getAtendente());
    }

    @Test
    void criaMensagemDeAtendenteIdentificandoQuemEnviou() {
        Empresa empresa = new Empresa(1L, "WXN");

        Atendente atendente = new Atendente(
                1L,
                "Gabriel",
                "gabriel@wxn.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE);

        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        conversa.solicitarAtendimentoHumano();
        conversa.iniciarAtendimentoHumano(atendente);
        Mensagem mensagem = new Mensagem(
                3L,
                conversa,
                "Vou verificar sua solicitacao",
                Mensagem.TipoRemetente.ATENDENTE,
                atendente);

        assertEquals(Mensagem.TipoRemetente.ATENDENTE, mensagem.getRemetente());
        assertSame(atendente, mensagem.getAtendente());
    }

    @Test
    void rejeitaIdNuloZeroOuNegativo() {
        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        assertThrows(
                NullPointerException.class,
                () -> new Mensagem(
                        null,
                        conversa,
                        "Oi",
                        Mensagem.TipoRemetente.CLIENTE));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        0L,
                        conversa,
                        "Oi",
                        Mensagem.TipoRemetente.CLIENTE));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        -1L,
                        conversa,
                        "Oi",
                        Mensagem.TipoRemetente.CLIENTE));
    }

    @Test
    void rejeitaConversaNula() {
        assertThrows(
                NullPointerException.class,
                () -> new Mensagem(
                        1L,
                        null,
                        "Oi",
                        Mensagem.TipoRemetente.CLIENTE));
    }

    @Test
    void rejeitaConteudoNuloOuEmBranco() {
        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        assertThrows(
                NullPointerException.class,
                () -> new Mensagem(
                        1L,
                        conversa,
                        null,
                        Mensagem.TipoRemetente.CLIENTE));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        1L,
                        conversa,
                        "",
                        Mensagem.TipoRemetente.CLIENTE));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        1L,
                        conversa,
                        "   ",
                        Mensagem.TipoRemetente.CLIENTE));
    }

    @Test
    void rejeitaRemetenteNulo() {
        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        assertThrows(
                NullPointerException.class,
                () -> new Mensagem(
                        1L,
                        conversa,
                        "Oi",
                        null));
    }

    @Test
    void naoPermiteAtendenteEmMensagemDeClienteOuChatbot() {
        Empresa empresa = new Empresa(1L, "WXN");

        Atendente atendente = new Atendente(
                1L,
                "Gabriel",
                "gabriel@wxn.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE);

        Cliente cliente = new Cliente(1L, "81999999999");
        Conversa conversa = new Conversa(1L, cliente, empresa, chatbot);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        1L,
                        conversa,
                        "Oi",
                        Mensagem.TipoRemetente.CLIENTE,
                        atendente));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Mensagem(
                        2L,
                        conversa,
                        "Oi",
                        Mensagem.TipoRemetente.CHATBOT,
                        atendente));
    }
}