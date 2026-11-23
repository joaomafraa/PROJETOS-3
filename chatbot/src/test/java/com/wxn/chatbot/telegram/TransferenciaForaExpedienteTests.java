package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.ConfiguracaoAtendimento;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:transferencia-fora-expediente;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class TransferenciaForaExpedienteTests {

    private static final String MENSAGEM = "Estamos fora do horário. Atendemos de segunda a sexta.";

    @Autowired private EntityManager em;
    @Autowired private IdentificacaoTelegramService identificacao;
    @Autowired private TransferenciaTelegramService transferencia;

    private Long conversaA;
    private Long conversaB;

    @BeforeEach
    void preparar() {
        Empresa a = new Empresa(85001L, "Empresa A");
        Empresa b = new Empresa(85002L, "Empresa B");
        em.persist(a);
        em.persist(b);
        em.persist(new Chatbot(85001L, "Bot A", a, "Olá"));
        em.persist(new Chatbot(85002L, "Bot B", b, "Olá"));
        Atendente admin = new Atendente(85001L, "Ana", "ana@fora-expediente.example", a,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        em.persist(admin);

        ConfiguracaoAtendimento config = new ConfiguracaoAtendimento(a);
        config.atualizar("America/Recife", MENSAGEM, List.of(), List.of(), admin);
        em.persist(config);
        em.flush();

        conversaA = identificacao.obterConversa(85001L, 85001L, 9001L, "Carlos");
        conversaB = identificacao.obterConversa(85002L, 85002L, 9001L, "Carlos");
    }

    @Test
    void foraDoExpedienteEntraNaFilaEAvisaOCliente() {
        String resposta = transferencia.solicitar(85001L, conversaA);

        assertTrue(resposta.startsWith(MENSAGEM));
        assertTrue(resposta.contains("ficou registrada na fila"));
        assertNotEquals(TransferenciaTelegramService.CONFIRMACAO, resposta);

        // A fila humana continua sendo usada: a conversa aguarda um atendente.
        Conversa conversa = em.find(Conversa.class, conversaA);
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, conversa.getStatus());

        // O que o cliente recebeu e o que fica no historico do painel.
        Mensagem ultima = conversa.getMensagens().get(conversa.getMensagens().size() - 1);
        assertEquals(Mensagem.TipoRemetente.CHATBOT, ultima.getRemetente());
        assertEquals(resposta, ultima.getConteudo());
    }

    @Test
    void pedirDeNovoNaoDuplicaAMensagemNoHistorico() {
        String primeira = transferencia.solicitar(85001L, conversaA);
        int mensagens = em.find(Conversa.class, conversaA).getMensagens().size();

        String segunda = transferencia.solicitar(85001L, conversaA);

        assertEquals(primeira, segunda);
        assertEquals(mensagens, em.find(Conversa.class, conversaA).getMensagens().size());
    }

    @Test
    void empresaSemConfiguracaoContinuaComAMensagemDeSempre() {
        assertEquals(TransferenciaTelegramService.CONFIRMACAO, transferencia.solicitar(85002L, conversaB));
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, em.find(Conversa.class, conversaB).getStatus());
    }
}
