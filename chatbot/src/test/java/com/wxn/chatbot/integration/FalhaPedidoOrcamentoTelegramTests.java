package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

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
import com.wxn.chatbot.service.PedidoOrcamentoService;
import com.wxn.chatbot.telegram.FalhaEtapaTelegramException;
import com.wxn.chatbot.telegram.MensagemTelegram;
import com.wxn.chatbot.telegram.ProcessarMensagemTelegramService;
import com.wxn.chatbot.telegram.RespostaTelegramService;
import com.wxn.chatbot.telegram.TelegramCliente;

import jakarta.persistence.EntityManager;

// HU06.1: se o registro falhar, o cliente nao recebe uma confirmacao de pedido inexistente.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pedido-orcamento-falha;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class FalhaPedidoOrcamentoTelegramTests {

    private static final long EMPRESA = 84001L;
    private static final long CHATBOT = 84001L;
    private static final long USUARIO = 779L;
    private static final String TELEFONE = "+5581977770009";
    private static final String NECESSIDADE = "Preciso de um orçamento para 20 licenças do sistema.";

    @Autowired EntityManager em;
    @Autowired ProcessarMensagemTelegramService processamento;
    @MockitoBean TelegramCliente telegram;
    @MockitoBean RespostaTelegramService respostas;
    @MockitoBean PedidoOrcamentoService pedidos;

    private long proximaAtualizacao = 7000;

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(EMPRESA, "Empresa Exemplo");
        em.persist(empresa);
        em.persist(new Chatbot(CHATBOT, "Bot", empresa, "Ola"));
        Cliente marina = new Cliente(84001L, TELEFONE);
        marina.alterarNome("Marina Costa");
        marina.vincularEmpresa(empresa);
        em.persist(marina);
        em.flush();
        when(respostas.respostaExistenteOuPendente(anyLong(), anyLong())).thenReturn("Resposta da IA");
        when(pedidos.contatoCadastrado(anyLong(), anyLong())).thenReturn(Optional.of("Telefone: " + TELEFONE));
        enviar("Oi");
        var contato = MensagemTelegram.interpretar(java.util.Map.of("update_id", proximaAtualizacao++, "message", java.util.Map.of(
                "chat", java.util.Map.of("id", USUARIO, "type", "private"),
                "from", java.util.Map.of("id", USUARIO, "first_name", "Marina"),
                "contact", java.util.Map.of("phone_number", TELEFONE, "user_id", USUARIO)))).orElseThrow();
        processamento.processar(EMPRESA, CHATBOT, contato);
    }

    @Test
    void falhaNoRegistroNaoGeraConfirmacaoNemEnvioPreparado() {
        when(pedidos.registrar(anyLong(), anyLong(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("banco indisponível"));
        MensagemTelegram pedido = new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Marina", "/orcamento " + NECESSIDADE);

        FalhaEtapaTelegramException falha = assertThrows(FalhaEtapaTelegramException.class,
                () -> processamento.processar(EMPRESA, CHATBOT, pedido));

        assertEquals("pedido_orcamento", falha.etapa());
        assertFalse(falha.getMessage().contains(NECESSIDADE), "O diagnóstico não deve conter o texto do cliente.");
        verify(telegram, never()).enviarMensagem(anyLong(), contains("foi registrado"));
        verify(telegram, never()).enviarMensagem(anyLong(), contains("Protocolo"));
        em.flush();
        assertEquals(0L, numero("select count(*) from tickets"));
        assertEquals(0L, numero("select count(*) from telegram_envios where atualizacao_id = " + pedido.atualizacaoId()));
    }

    private void enviar(String texto) {
        processamento.processar(EMPRESA, CHATBOT, new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Marina", texto));
    }

    private long numero(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
