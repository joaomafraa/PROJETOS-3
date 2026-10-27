package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.MensagemResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.FilaConversasService;
import com.wxn.chatbot.service.HistoricoConversasService;
import com.wxn.chatbot.web.PainelHistoricoController;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:historico;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "painel-local"})
class HistoricoPersistenciaTests {
    @Autowired private EntityManager entityManager;
    @Autowired private ConversaRepository repository;
    @Autowired private FilaConversasService filaService;
    @Autowired private HistoricoConversasService historicoService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PainelHistoricoController painel;

    @Test
    void persisteRecarregaEConsultaForaDaTransacaoSemVazarOutraEmpresa() {
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        Empresa empresa = new Empresa(1L, "WXN");
        Empresa outra = new Empresa(2L, "Outra");
        Atendente ana = new Atendente(1L, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE);
        Atendente admin = new Atendente(2L, "Admin", "admin@example.com", empresa, Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente externo = new Atendente(3L, "Externo", "externo@example.com", outra, Atendente.PerfilAcesso.ATENDENTE);

        transacao.executeWithoutResult(status -> {
            entityManager.persist(empresa);
            entityManager.persist(outra);
            entityManager.persist(ana);
            entityManager.persist(admin);
            entityManager.persist(externo);
            Cliente cliente = new Cliente(1L, "+5581999999999");
            cliente.alterarNome("Marina");
            cliente.vincularEmpresa(empresa);
            Chatbot bot = new Chatbot(1L, "Bot", empresa, "Ola");
            entityManager.persist(cliente);
            entityManager.persist(bot);
            Conversa conversa = new Conversa(1L, cliente, empresa, bot);
            conversa.adicionarMensagem(20L, "Pergunta", Mensagem.TipoRemetente.CLIENTE);
            conversa.adicionarMensagem(10L, "Resposta", Mensagem.TipoRemetente.CHATBOT);
            conversa.solicitarAtendimentoHumano();
            conversa.iniciarAtendimentoHumano(ana);
            conversa.adicionarMensagem(30L, "Atendimento", Mensagem.TipoRemetente.ATENDENTE, ana);
            conversa.encerrar();
            repository.saveAndFlush(conversa);
            Conversa aguardando = new Conversa(2L, cliente, empresa, bot);
            // O mesmo id de mensagem pode existir em conversas diferentes.
            aguardando.adicionarMensagem(20L, "Outro pedido", Mensagem.TipoRemetente.CLIENTE);
            aguardando.solicitarAtendimentoHumano();
            repository.saveAndFlush(aguardando);
            cliente.trocarEmpresa(outra, admin);
        });

        // Servicos encerram suas transacoes antes destas leituras.
        var fila = filaService.visualizarFila(admin);
        assertEquals(1, fila.size());
        assertEquals(2L, fila.getFirst().id());
        assertEquals("Marina", fila.getFirst().clienteNome());
        assertEquals("Bot", fila.getFirst().chatbotNome());
        assertThrows(UnsupportedOperationException.class, fila::clear);
        var historico = historicoService.visualizarHistorico(1L, admin);
        assertEquals(List.of(20L, 10L, 30L), historico.stream().map(MensagemResumo::id).toList());
        assertEquals(List.of("Pergunta", "Resposta", "Atendimento"), historico.stream().map(MensagemResumo::conteudo).toList());
        assertEquals("Ana", historico.getLast().autor());
        assertEquals(ana.getId(), historico.getLast().atendenteId());
        assertThrows(UnsupportedOperationException.class, historico::clear);
        assertTrue(filaService.visualizarFila(externo).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> historicoService.visualizarHistorico(1L, externo));
        assertThrows(IllegalArgumentException.class, () -> historicoService.visualizarHistorico(999L, admin));
        assertThrows(IllegalArgumentException.class, () -> historicoService.visualizarHistorico(0L, admin));
        assertThrows(NullPointerException.class, () -> historicoService.visualizarHistorico(1L, null));
        var conversasDoPainel = painel.listar();
        assertEquals(2, conversasDoPainel.size());
        assertEquals(2L, conversasDoPainel.getFirst().id());
        assertEquals("Marina", conversasDoPainel.getFirst().clienteNome());
        assertEquals("Atendimento", painel.mensagens(1L).getLast().conteudo());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> painel.mensagens(999L)).getStatusCode());

        transacao.executeWithoutResult(status -> {
            Atendente autor = entityManager.find(Atendente.class, 1L);
            autor.desativar(entityManager.find(Atendente.class, 2L));
            Conversa conversa = repository.findById(1L).orElseThrow();
            assertEquals(3, conversa.getMensagens().size());
            assertThrows(IllegalStateException.class,
                    () -> conversa.adicionarMensagem(40L, "Depois do fim", Mensagem.TipoRemetente.CLIENTE));
        });
        assertEquals("Ana", historicoService.visualizarHistorico(1L, admin).getLast().autor());
        ana.desativar(admin);
        assertThrows(SecurityException.class, () -> historicoService.visualizarHistorico(1L, ana));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> painel.listar()).getStatusCode());
    }
}
