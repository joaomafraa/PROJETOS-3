package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.PedidoOrcamentoRepository;
import com.wxn.chatbot.telegram.MensagemTelegram;
import com.wxn.chatbot.telegram.ProcessarMensagemTelegramService;
import com.wxn.chatbot.telegram.RespostaTelegramService;
import com.wxn.chatbot.telegram.TelegramCliente;
import com.wxn.chatbot.telegram.TelegramCliente.Teclado;

import jakarta.persistence.EntityManager;

// HU06.1 pelo Telegram: do /orcamento ate o protocolo, sem interferir nas outras opcoes do menu.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pedido-orcamento-telegram;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class PedidoOrcamentoTelegramTests {

    private static final long EMPRESA = 83001L;
    private static final long OUTRA_EMPRESA = 83002L;
    private static final long CHATBOT = 83001L;
    private static final long OUTRO_CHATBOT = 83002L;
    private static final long USUARIO = 777L;
    private static final long OUTRO_USUARIO = 778L;
    private static final String TELEFONE = "+5581977770001";
    private static final String TELEFONE_DA_OUTRA = "+5581966660002";
    private static final String NECESSIDADE = "Preciso de um orçamento para 20 licenças do sistema.";

    @Autowired EntityManager em;
    @Autowired ProcessarMensagemTelegramService processamento;
    @Autowired PedidoOrcamentoRepository contatos;
    @MockitoBean TelegramCliente telegram;
    @MockitoBean RespostaTelegramService respostas;

    private long proximaAtualizacao = 5000;

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(EMPRESA, "Empresa Exemplo");
        Empresa outra = new Empresa(OUTRA_EMPRESA, "Empresa Alfa");
        em.persist(empresa);
        em.persist(outra);
        em.persist(new Chatbot(CHATBOT, "Bot", empresa, "Ola"));
        em.persist(new Chatbot(OUTRO_CHATBOT, "Bot Alfa", outra, "Ola"));
        Cliente marina = new Cliente(83001L, TELEFONE);
        marina.alterarNome("Marina Costa");
        marina.vincularEmpresa(empresa);
        em.persist(marina);
        Cliente paulo = new Cliente(83002L, TELEFONE_DA_OUTRA);
        paulo.alterarNome("Paulo Lima");
        paulo.vincularEmpresa(outra);
        em.persist(paulo);
        em.flush();
        when(respostas.respostaExistenteOuPendente(anyLong(), anyLong())).thenReturn("Resposta da IA");
        enviar("Oi");
        enviarContato(TELEFONE);
    }

    // ---------- Fluxo principal ----------

    @Test
    void registraOrcamentoUmaVezEConfirmaOProtocoloSemPrometerProposta() {
        assertTrue(enviar("/orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Descreva o que você precisa orçar"));
        assertEquals(0L, numero("select count(*) from tickets"));

        MensagemTelegram necessidade = new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Marina", NECESSIDADE);
        assertTrue(processamento.processar(EMPRESA, CHATBOT, necessidade));
        em.flush();

        assertEquals(1L, numero("select count(*) from tickets where empresa_id = " + EMPRESA));
        assertEquals("ORCAMENTO", texto("select tipo from tickets"));
        assertEquals(NECESSIDADE, texto("select descricao from tickets"));
        assertEquals(83001L, numero("select cliente_id from tickets"));
        String protocolo = texto("select protocolo from tickets");
        assertTrue(protocolo.startsWith("PD-"));
        assertEquals("Telefone: " + TELEFONE, texto("select contato from pedidos_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), argThat((String m) -> m.contains(protocolo)
                && m.contains("pedido de orçamento foi registrado") && m.contains("pelos dados do seu cadastro")
                && m.contains("não é uma proposta")));
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"), "A etapa temporária deve sumir.");
        assertEquals(0L, numero("select count(*) from leads"), "Não deve criar lead presumido.");

        // A mesma atualizacao do Telegram nao cria outro pedido nem confirma de novo.
        assertFalse(processamento.processar(EMPRESA, CHATBOT, necessidade));
        em.flush();
        assertEquals(1L, numero("select count(*) from tickets"));
        assertEquals(1L, numero("select count(*) from pedidos_orcamento"));
        verify(telegram, times(1)).enviarMensagem(eq(USUARIO), contains(protocolo));
    }

    @Test
    void necessidadeNoProprioComandoRegistraDireto() {
        assertTrue(enviar("/orcamento " + NECESSIDADE));
        em.flush();

        assertEquals(1L, numero("select count(*) from tickets"));
        assertEquals(NECESSIDADE, texto("select descricao from tickets"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("pedido de orçamento foi registrado"));
        verify(telegram, never()).enviarMensagem(eq(USUARIO), contains("Descreva o que você precisa orçar"));
    }

    @Test
    void novoPedidoDepoisDeConcluidoGeraOutroProtocolo() {
        enviar("/orcamento " + NECESSIDADE);
        enviar("/orcamento Preciso também de treinamento para a equipe.");
        em.flush();

        assertEquals(2L, numero("select count(*) from tickets"));
        assertEquals(2L, numero("select count(distinct protocolo) from tickets"));
    }

    // ---------- Cancelamento e falhas de entrada ----------

    @Test
    void cancelarNaoRegistraNada() {
        enviar("/orcamento");
        enviar("/cancelar");
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals(0L, numero("select count(*) from pedidos_orcamento"));
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Nenhuma solicitação foi criada"));
        verify(telegram, never()).enviarMensagem(eq(USUARIO), contains("foi registrado"));
    }

    @Test
    void necessidadeCurtaMantemOPedidoAbertoENaoRegistra() {
        enviar("/orcamento");
        enviar("preço");
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals("NECESSIDADE", texto("select etapa from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("de 10 a 2.000 caracteres ou envie /cancelar"));

        enviar(NECESSIDADE);
        em.flush();
        assertEquals(1L, numero("select count(*) from tickets"));
    }

    @Test
    void necessidadeLongaDemaisNaoRegistra() {
        enviar("/orcamento");
        enviar("x".repeat(2001));
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals("NECESSIDADE", texto("select etapa from telegram_pedido_orcamento"));
    }

    @Test
    void comandoComNecessidadeCurtaPedeADescricaoDeNovo() {
        enviar("/orcamento preço");
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals("NECESSIDADE", texto("select etapa from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("de 10 a 2.000 caracteres ou envie /cancelar"));
    }

    // ---------- Contato ausente no cadastro ----------

    @Test
    void semContatoNoCadastroPedeSomenteOContato() {
        apagarTelefoneDoCliente();

        enviar("/orcamento");
        enviar(NECESSIDADE);
        em.flush();
        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals("CONTATO", texto("select etapa from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Não encontrei telefone nem e-mail no seu cadastro"));

        enviar("isso não é um contato");
        em.flush();
        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals("CONTATO", texto("select etapa from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Não entendi o contato"));

        enviar("Marina@Example.com");
        em.flush();
        assertEquals(1L, numero("select count(*) from tickets"));
        assertEquals(NECESSIDADE, texto("select descricao from tickets"));
        assertEquals("E-mail: marina@example.com", texto("select contato from pedidos_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("pelo contato que você informou"));
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        // O contato digitado vale so para este pedido: nao altera o cadastro nem a identidade.
        assertNull(texto("select numero_telefone from clientes where id = 83001"));
        assertNull(texto("select email from clientes where id = 83001"));
    }

    @Test
    void cancelarNaEtapaDoContatoNaoRegistra() {
        apagarTelefoneDoCliente();
        enviar("/orcamento " + NECESSIDADE);
        enviar("/cancelar");
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
    }

    // ---------- Convivencia com as outras opcoes ----------

    @Test
    void outraOpcaoDuranteOOrcamentoExigeConcluirOuCancelar() {
        enviar("/orcamento");
        for (String comando : java.util.List.of("/chamado", "/demonstracao", "/reuniao", "/visita", "/orcamento")) {
            enviar(comando);
        }
        em.flush();

        verify(telegram, times(5)).enviarMensagem(eq(USUARIO), contains("Conclua o pedido de orçamento"));
        assertEquals(0L, numero("select count(*) from telegram_abertura_chamado"));
        assertEquals(0L, numero("select count(*) from telegram_pedido_agendamento"));

        enviar("/cancelar");
        enviar("/chamado");
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Descreva o problema para abrir seu chamado"));
    }

    @Test
    void orcamentoDuranteChamadoOuAgendamentoNaoIniciaOrcamento() {
        enviar("/chamado");
        enviar("/orcamento");
        em.flush();
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Informe uma descrição do problema"));
        enviar("/cancelar");

        enviar("/reuniao");
        enviar("/orcamento");
        em.flush();
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Conclua o pedido atual"));
        assertEquals(0L, numero("select count(*) from tickets"));
    }

    @Test
    void pedidosDeAgendamentoEChamadosContinuamFuncionando() {
        enviar("/demonstracao");
        enviar("Quero conhecer melhor o serviço.");
        enviar("Segunda às 14h");
        enviar("/chamado");
        enviar("Não consigo acessar o sistema desde ontem.");
        enviar("/orcamento " + NECESSIDADE);
        em.flush();

        assertEquals(1L, numero("select count(*) from tickets where tipo = 'DEMONSTRACAO'"));
        assertEquals(1L, numero("select count(*) from tickets where tipo = 'SUPORTE'"));
        assertEquals(1L, numero("select count(*) from tickets where tipo = 'ORCAMENTO'"));
        assertEquals(1L, numero("select count(*) from preferencias_agendamento"));
        assertEquals(1L, numero("select count(*) from pedidos_orcamento"));
    }

    @Test
    void comandosDoMenuContinuamRespondendoNoMeioDoPedido() {
        enviar("/orcamento");
        enviar("/menu");

        verify(telegram).enviarMensagem(eq(USUARIO), contains("solicite orçamento"), eq(Teclado.MENU));
        assertEquals("NECESSIDADE", texto("select etapa from telegram_pedido_orcamento"));
    }

    @Test
    void conversaComAtendenteNaoEhInterceptadaEAEtapaAntigaEhDescartada() {
        enviar("/orcamento");
        enviar("/atendente");
        em.flush();
        em.createNativeQuery("update conversas set status = 'EM_ATENDIMENTO_HUMANO'").executeUpdate();
        em.clear();
        when(respostas.respostaExistenteOuPendente(anyLong(), anyLong())).thenReturn(null);

        assertFalse(enviar(NECESSIDADE));
        em.flush();

        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        verify(telegram, never()).enviarMensagem(eq(USUARIO), contains("foi registrado"));
    }

    @Test
    void comandoSemFluxoAtivoNaoEhInterceptadoPorOutrosTextos() {
        enviar("Quero saber sobre o orçamento do plano anual");
        enviar("/cancelar");
        em.flush();

        assertEquals(0L, numero("select count(*) from telegram_pedido_orcamento"));
        assertEquals(0L, numero("select count(*) from tickets"));
        verify(telegram, times(2)).enviarMensagem(USUARIO, "Resposta da IA");
    }

    // ---------- Isolamento ----------

    @Test
    void cadaPedidoFicaNaEmpresaEnoClienteDaPropriaConversa() {
        enviar("/orcamento " + NECESSIDADE);
        enviarComoOutro("Oi");
        enviarContatoComoOutro(TELEFONE_DA_OUTRA);
        enviarComoOutro("/orcamento Orçamento de consultoria para a Alfa.");
        em.flush();

        assertEquals(1L, numero("select count(*) from tickets where empresa_id = " + EMPRESA));
        assertEquals(1L, numero("select count(*) from tickets where empresa_id = " + OUTRA_EMPRESA));
        assertEquals(83001L, numero("select cliente_id from tickets where empresa_id = " + EMPRESA));
        assertEquals(83002L, numero("select cliente_id from tickets where empresa_id = " + OUTRA_EMPRESA));
        assertEquals("Telefone: " + TELEFONE, texto("select contato from pedidos_orcamento where empresa_id = " + EMPRESA));
        assertEquals("Telefone: " + TELEFONE_DA_OUTRA,
                texto("select contato from pedidos_orcamento where empresa_id = " + OUTRA_EMPRESA));
        long ticketDaEmpresa = numero("select id from tickets where empresa_id = " + EMPRESA);
        assertTrue(contatos.findByTicketIdAndEmpresaId(ticketDaEmpresa, OUTRA_EMPRESA).isEmpty());
        assertTrue(contatos.findByTicketIdAndEmpresaId(ticketDaEmpresa, EMPRESA).isPresent());
    }

    @Test
    void etapaEmAndamentoNaoVazaParaOutroUsuarioNemOutraEmpresa() {
        enviar("/orcamento");
        enviarComoOutro("Oi");
        enviarContatoComoOutro(TELEFONE_DA_OUTRA);
        enviarComoOutro(NECESSIDADE); // sem /orcamento, nao e um pedido

        em.flush();
        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals(1L, numero("select count(*) from telegram_pedido_orcamento"));
        assertEquals(EMPRESA, numero("select empresa_id from telegram_pedido_orcamento"));
    }

    // ---------- Auxiliares ----------

    private void apagarTelefoneDoCliente() {
        em.flush();
        em.createNativeQuery("update clientes set numero_telefone = null where id = 83001").executeUpdate();
        em.clear();
    }

    private boolean enviar(String texto) {
        return processamento.processar(EMPRESA, CHATBOT,
                new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Marina", texto));
    }

    private boolean enviarContato(String telefone) {
        Map<String, Object> update = Map.of("update_id", proximaAtualizacao++, "message", Map.of(
                "chat", Map.of("id", USUARIO, "type", "private"),
                "from", Map.of("id", USUARIO, "first_name", "Marina"),
                "contact", Map.of("phone_number", telefone, "user_id", USUARIO)));
        return processamento.processar(EMPRESA, CHATBOT, MensagemTelegram.interpretar(update).orElseThrow());
    }

    private boolean enviarComoOutro(String texto) {
        return processamento.processar(OUTRA_EMPRESA, OUTRO_CHATBOT,
                new MensagemTelegram(proximaAtualizacao++, OUTRO_USUARIO, OUTRO_USUARIO, "Paulo", texto));
    }

    private boolean enviarContatoComoOutro(String telefone) {
        Map<String, Object> update = Map.of("update_id", proximaAtualizacao++, "message", Map.of(
                "chat", Map.of("id", OUTRO_USUARIO, "type", "private"),
                "from", Map.of("id", OUTRO_USUARIO, "first_name", "Paulo"),
                "contact", Map.of("phone_number", telefone, "user_id", OUTRO_USUARIO)));
        return processamento.processar(OUTRA_EMPRESA, OUTRO_CHATBOT, MensagemTelegram.interpretar(update).orElseThrow());
    }

    private long numero(String sql) {
        em.flush();
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private String texto(String sql) {
        em.flush();
        return (String) em.createNativeQuery(sql).getSingleResult();
    }
}
