package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.repository.TicketRepository;
import com.wxn.chatbot.service.AbrirChamadoService;
import com.wxn.chatbot.service.NotificacaoChamadosService;

import jakarta.persistence.EntityManager;

// HU05.3: se gerar as notificacoes falhar, o chamado ja registrado NAO pode ser desfeito
// e o cliente continua recebendo o protocolo. O servico de notificacao e trocado por um
// que sempre falha.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:falha-notificacao-chamado;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class FalhaNotificacaoChamadoTests {

    @MockitoBean private NotificacaoChamadosService notificacoes;

    @Autowired private TransactionTemplate transacao;
    @Autowired private EntityManager em;
    @Autowired private AbrirChamadoService abrirChamado;
    @Autowired private TicketRepository tickets;

    @Test
    void falhaNaNotificacaoNaoApagaOChamado() {
        transacao.executeWithoutResult(s -> {
            Empresa empresa = new Empresa(71L, "Empresa A");
            em.persist(empresa);
            Cliente cliente = new Cliente(7001L, "+5581999907001");
            cliente.vincularEmpresa(empresa);
            em.persist(cliente);
            Chatbot bot = new Chatbot(7001L, "Bot", empresa, "Olá");
            em.persist(bot);
            em.persist(new Conversa(7001L, cliente, empresa, bot));
        });
        when(notificacoes.notificarNovoChamado(anyLong())).thenThrow(new IllegalStateException("banco fora do ar"));

        TicketAberto chamado = abrirChamado.abrir(71L, 7001L, "Sistema lento");

        assertTrue(chamado.protocolo().startsWith("CH-"));
        String salvo = transacao.execute(s -> tickets.findById(chamado.id()).orElseThrow().getProtocolo());
        assertEquals(chamado.protocolo(), salvo);
    }
}
