package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.security.EnvioCodigoSmtp;
import com.wxn.chatbot.security.ValidacaoClienteService;
import com.wxn.chatbot.telegram.MensagemTelegram;
import com.wxn.chatbot.telegram.ProcessarMensagemTelegramService;
import com.wxn.chatbot.telegram.TelegramCliente;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:validacao-cliente;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ValidacaoClienteTests {
    @Autowired EntityManager em;
    @Autowired ValidacaoClienteService validacao;
    @MockitoBean EnvioCodigoSmtp email;
    @MockitoBean TelegramCliente telegram;
    @Autowired ProcessarMensagemTelegramService processamento;

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(81001L, "Empresa de teste");
        Empresa outra = new Empresa(81002L, "Outra empresa");
        em.persist(empresa); em.persist(outra);
        Cliente cliente = new Cliente(81001L, "81988887777");
        cliente.vincularEmpresa(empresa); cliente.atualizarEmail("cliente@example.test"); em.persist(cliente);
        Cliente outro = new Cliente(81002L, "81988886666");
        outro.vincularEmpresa(empresa); outro.atualizarEmail("outro@example.test"); em.persist(outro);
        Chatbot bot = new Chatbot(81001L, "Bot", empresa, "Ola"); em.persist(bot);
        Conversa conversa = new Conversa(81001L, cliente, empresa, bot); em.persist(conversa);
        Conversa conversaOutro = new Conversa(81002L, outro, empresa, bot); em.persist(conversaOutro);
        em.persist(new Ticket(81001L, "VALIDACAO-1", "Pedido", Ticket.TipoSolicitacao.SUPORTE, conversa));
        em.persist(new Ticket(81002L, "VALIDACAO-2", "Outro", Ticket.TipoSolicitacao.SUPORTE, conversaOutro));
        em.flush();
        em.createNativeQuery("insert into emails_confiaveis_cliente (cliente_id, empresa_id, email) values (81001, 81001, 'cliente@example.test')").executeUpdate();
    }

    private String solicitarCodigo() {
        validacao.solicitar(81001L, 81001L);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq("cliente@example.test"), codigo.capture());
        assertTrue(codigo.getValue().matches("[0-9]{6}"));
        return codigo.getValue();
    }

    @Test
    void exigeCodigoEConsultaSomenteSolicitacoesDoCliente() {
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81001L, 81001L));
        String codigo = solicitarCodigo();
        assertTrue(validacao.confirmar(81001L, 81001L, codigo));
        assertFalse(validacao.confirmar(81001L, 81001L, codigo));
        var itens = validacao.consultarSolicitacoes(81001L, 81001L);
        assertEquals(1, itens.size()); assertEquals("VALIDACAO-1", itens.get(0).protocolo());
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81001L, 81002L));
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81002L, 81001L));
    }

    @Test
    void bloqueiaAposCincoErrosEImpedeNovoCodigoAteExpirar() {
        String codigo = solicitarCodigo();
        String errado = codigo.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) assertFalse(validacao.confirmar(81001L, 81001L, errado));
        assertFalse(validacao.confirmar(81001L, 81001L, codigo));
        assertThrows(IllegalStateException.class, () -> validacao.solicitar(81001L, 81001L));
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81001L, 81001L));
    }

    @Test
    void expiraCodigoERevogaQuandoEnderecoMuda() {
        String codigo = solicitarCodigo();
        em.createNativeQuery("update desafios_cliente set expira_em=:expira where conversa_id=81001")
                .setParameter("expira", Instant.now().minusSeconds(1)).executeUpdate();
        em.clear();
        assertFalse(validacao.confirmar(81001L, 81001L, codigo));
        em.find(Cliente.class, 81001L).atualizarEmail("novo@example.test");
        assertThrows(SecurityException.class, () -> validacao.solicitar(81001L, 81001L));
    }

    @Test
    void emailNaoVerificadoNaoPermiteEnviar() {
        assertThrows(SecurityException.class, () -> validacao.solicitar(81001L, 81002L));
        verifyNoInteractions(email);
    }

    @Test
    void permissaoExpiradaNaoLiberaConsulta() {
        String codigo = solicitarCodigo();
        assertTrue(validacao.confirmar(81001L, 81001L, codigo));
        em.flush();
        em.createNativeQuery("update desafios_cliente set validado_ate=:expira where conversa_id=81001")
                .setParameter("expira", Instant.now().minusSeconds(1)).executeUpdate();
        em.clear();
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81001L, 81001L));
    }

    @Test
    void alteracaoDeEmailRevogaPermissaoJaConcedida() {
        String codigo = solicitarCodigo();
        assertTrue(validacao.confirmar(81001L, 81001L, codigo));
        em.find(Cliente.class, 81001L).atualizarEmail("novo@example.test");
        assertThrows(SecurityException.class, () -> validacao.consultarSolicitacoes(81001L, 81001L));
    }

    @Test
    void telegramProtegeConsultaEOmiteCodigoDoHistorico() {
        em.createNativeQuery("insert into telegram_contatos (empresa_id, usuario_id, cliente_id, conversa_id) values (81001, 777, 81001, 81001)").executeUpdate();
        assertTrue(processamento.processar(81001, 81001, new MensagemTelegram(11, 777, 777, "Teste", "/solicitacoes")));
        verify(telegram).enviarMensagem(eq(777L), contains("valide sua identidade"));
        assertTrue(processamento.processar(81001, 81001, new MensagemTelegram(12, 777, 777, "Teste", "/validar")));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq("cliente@example.test"), captor.capture());
        String codigo = captor.getValue();
        assertTrue(processamento.processar(81001, 81001, new MensagemTelegram(13, 777, 777, "Teste", "/codigo " + codigo)));
        assertTrue(processamento.processar(81001, 81001, new MensagemTelegram(14, 777, 777, "Teste", "/solicitacoes")));
        verify(telegram).enviarMensagem(eq(777L), contains("VALIDACAO-1"));
        em.flush();
        assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from mensagens where conteudo like :codigo")
                .setParameter("codigo", "%" + codigo + "%").getSingleResult()).longValue());
    }
}
