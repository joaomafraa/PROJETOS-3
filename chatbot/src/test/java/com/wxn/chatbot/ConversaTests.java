package com.wxn.chatbot;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConversaTests {

    private final Cliente cliente = new Cliente("81999999999");

    @Test
    void iniciaConversaAbertaComClienteESemMensagens() {
        Conversa conversa = novaConversa();

        assertAll(
                () -> assertEquals(1L, conversa.getId()),
                () -> assertSame(cliente, conversa.getCliente()),
                () -> assertNotNull(conversa.getDataInicio()),
                () -> assertNull(conversa.getDataEncerramento()),
                () -> assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, conversa.getStatus()),
                () -> assertTrue(conversa.getMensagens().isEmpty()));
    }

    @Test
    void exigeIdentificadorPositivoEClienteNaCriacao() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Conversa(null, cliente)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Conversa(0L, cliente)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Conversa(-1L, cliente)),
                () -> assertThrows(NullPointerException.class, () -> new Conversa(1L, null)));
    }

    @Test
    void mantemMensagensNaOrdemEVinculadasAConversaDuranteOAtendimento() {
        Conversa conversa = novaConversa();

        Mensagem pergunta = conversa.adicionarMensagem(10L, "Preciso de ajuda", Mensagem.TipoRemetente.CLIENTE);
        Mensagem resposta = conversa.adicionarMensagem(11L, "Como posso ajudar?", Mensagem.TipoRemetente.CHATBOT);
        conversa.solicitarAtendimentoHumano();
        conversa.iniciarAtendimentoHumano();
        Mensagem atendimento = conversa.adicionarMensagem(12L, "Vou verificar seu pedido", Mensagem.TipoRemetente.ATENDENTE);

        assertEquals(List.of(pergunta, resposta, atendimento), conversa.getMensagens());
        assertAll(
                () -> assertEquals(10L, pergunta.getId()),
                () -> assertEquals("Preciso de ajuda", pergunta.getConteudo()),
                () -> assertEquals(Mensagem.TipoRemetente.CLIENTE, pergunta.getRemetente()),
                () -> assertEquals(Mensagem.TipoRemetente.CHATBOT, resposta.getRemetente()),
                () -> assertEquals(Mensagem.TipoRemetente.ATENDENTE, atendimento.getRemetente()));
        for (Mensagem mensagem : conversa.getMensagens()) {
            assertSame(conversa, mensagem.getConversa());
            assertNotNull(mensagem.getDataEnvio());
            assertFalse(mensagem.getDataEnvio().isBefore(conversa.getDataInicio()));
        }
    }

    @Test
    void rejeitaDadosInvalidosSemAdicionarMensagens() {
        Conversa conversa = novaConversa();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> conversa.adicionarMensagem(null, "Oi", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversa.adicionarMensagem(0L, "Oi", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversa.adicionarMensagem(-1L, "Oi", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(NullPointerException.class,
                        () -> conversa.adicionarMensagem(10L, null, Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversa.adicionarMensagem(10L, "", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> conversa.adicionarMensagem(10L, " \t\n", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(NullPointerException.class,
                        () -> conversa.adicionarMensagem(10L, "Oi", null)));
        assertTrue(conversa.getMensagens().isEmpty());
    }

    @Test
    void rejeitaIdentificadorDeMensagemDuplicadoNaMesmaConversa() {
        Conversa conversa = novaConversa();
        Mensagem original = conversa.adicionarMensagem(10L, "Oi", Mensagem.TipoRemetente.CLIENTE);

        assertThrows(IllegalArgumentException.class,
                () -> conversa.adicionarMensagem(10L, "Resposta", Mensagem.TipoRemetente.CHATBOT));
        assertEquals(List.of(original), conversa.getMensagens());

        Conversa outraConversa = new Conversa(2L, cliente);
        Mensagem outraMensagem = outraConversa.adicionarMensagem(10L, "Outra conversa", Mensagem.TipoRemetente.CLIENTE);
        assertSame(outraConversa, outraMensagem.getConversa());
    }

    @Test
    void protegeHistoricoContraAlteracoesExternasEForneceUmaCopia() {
        Conversa conversa = novaConversa();
        Mensagem primeira = conversa.adicionarMensagem(10L, "Oi", Mensagem.TipoRemetente.CLIENTE);
        List<Mensagem> historico = conversa.getMensagens();

        assertThrows(UnsupportedOperationException.class, historico::clear);
        conversa.adicionarMensagem(11L, "Ola", Mensagem.TipoRemetente.CHATBOT);

        assertEquals(List.of(primeira), historico);
        assertEquals(2, conversa.getMensagens().size());
    }

    @Test
    void percorreTransferenciaParaHumanoEEncerramento() {
        Conversa conversa = novaConversa();

        conversa.solicitarAtendimentoHumano();
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, conversa.getStatus());
        assertNull(conversa.getDataEncerramento());

        conversa.iniciarAtendimentoHumano();
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, conversa.getStatus());
        assertNull(conversa.getDataEncerramento());

        conversa.encerrar();
        assertEquals(Conversa.Status.ENCERRADA, conversa.getStatus());
        assertNotNull(conversa.getDataEncerramento());
        assertFalse(conversa.getDataEncerramento().isBefore(conversa.getDataInicio()));
    }

    @Test
    void rejeitaTransicoesForaDaOrdemSemAlterarEstado() {
        Conversa conversa = novaConversa();

        assertThrows(IllegalStateException.class, conversa::iniciarAtendimentoHumano);
        assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, conversa.getStatus());

        conversa.solicitarAtendimentoHumano();
        assertThrows(IllegalStateException.class, conversa::solicitarAtendimentoHumano);
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, conversa.getStatus());

        conversa.iniciarAtendimentoHumano();
        assertAll(
                () -> assertThrows(IllegalStateException.class, conversa::solicitarAtendimentoHumano),
                () -> assertThrows(IllegalStateException.class, conversa::iniciarAtendimentoHumano));
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, conversa.getStatus());
        assertNull(conversa.getDataEncerramento());
    }

    @Test
    void permiteEncerrarTambemDuranteAtendimentoAutomaticoOuEspera() {
        Conversa automatica = novaConversa();
        Conversa aguardando = new Conversa(2L, cliente);
        aguardando.solicitarAtendimentoHumano();

        for (Conversa conversa : List.of(automatica, aguardando)) {
            conversa.encerrar();
            assertEquals(Conversa.Status.ENCERRADA, conversa.getStatus());
            assertNotNull(conversa.getDataEncerramento());
            assertFalse(conversa.getDataEncerramento().isBefore(conversa.getDataInicio()));
        }
    }

    @Test
    void conversaEncerradaPreservaHistoricoEDataERejeitaNovasOperacoes() {
        Conversa conversa = novaConversa();
        Mensagem mensagem = conversa.adicionarMensagem(10L, "Obrigado", Mensagem.TipoRemetente.CLIENTE);
        conversa.encerrar();
        var dataEncerramento = conversa.getDataEncerramento();

        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> conversa.adicionarMensagem(11L, "Nova mensagem", Mensagem.TipoRemetente.CLIENTE)),
                () -> assertThrows(IllegalStateException.class, conversa::solicitarAtendimentoHumano),
                () -> assertThrows(IllegalStateException.class, conversa::iniciarAtendimentoHumano),
                () -> assertThrows(IllegalStateException.class, conversa::encerrar));
        assertEquals(Conversa.Status.ENCERRADA, conversa.getStatus());
        assertEquals(dataEncerramento, conversa.getDataEncerramento());
        assertEquals(List.of(mensagem), conversa.getMensagens());
    }

    private Conversa novaConversa() {
        return new Conversa(1L, cliente);
    }
}
