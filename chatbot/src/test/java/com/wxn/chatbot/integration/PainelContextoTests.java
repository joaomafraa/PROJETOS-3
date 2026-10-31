package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.ConversaResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.web.PainelHistoricoController;

// Garante que a rota da fila entrega os dados exibidos no painel de contexto
// (cliente, telefone, chatbot, status e inicio) somente da empresa do atendente.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contexto;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "painel-local"})
class PainelContextoTests {
    @Autowired private EntityManager entityManager;
    @Autowired private ConversaRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PainelHistoricoController painel;

    @Test
    void filaEntregaDadosDoContextoSemVazarOutraEmpresa() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Empresa empresa = new Empresa(1L, "WXN");
            Empresa outra = new Empresa(2L, "Outra");
            entityManager.persist(empresa);
            entityManager.persist(outra);
            entityManager.persist(new Atendente(1L, "Ana", "ana@example.com", empresa,
                    Atendente.PerfilAcesso.ATENDENTE));

            Chatbot bot = new Chatbot(1L, "Assistente WXN", empresa, "Ola");
            Chatbot botExterno = new Chatbot(2L, "Bot externo", outra, "Ola");
            entityManager.persist(bot);
            entityManager.persist(botExterno);

            Cliente marina = new Cliente(1L, "+5581999999999");
            marina.alterarNome("Marina");
            marina.vincularEmpresa(empresa);
            Cliente semNome = new Cliente(2L, "+5581988887777");
            semNome.vincularEmpresa(empresa);
            Cliente externo = new Cliente(3L, "+5511977776666");
            externo.alterarNome("Cliente externo");
            externo.vincularEmpresa(outra);
            entityManager.persist(marina);
            entityManager.persist(semNome);
            entityManager.persist(externo);

            Conversa aguardando = new Conversa(1L, marina, empresa, bot);
            aguardando.adicionarMensagem(1L, "Preciso de ajuda", Mensagem.TipoRemetente.CLIENTE);
            aguardando.solicitarAtendimentoHumano();
            repository.saveAndFlush(aguardando);

            Conversa aguardandoSemNome = new Conversa(2L, semNome, empresa, bot);
            aguardandoSemNome.solicitarAtendimentoHumano();
            repository.saveAndFlush(aguardandoSemNome);

            // Nao deve aparecer: ainda no atendimento automatico.
            repository.saveAndFlush(new Conversa(3L, marina, empresa, bot));

            // Nao deve aparecer: outra empresa.
            Conversa daOutra = new Conversa(4L, externo, outra, botExterno);
            daOutra.solicitarAtendimentoHumano();
            repository.saveAndFlush(daOutra);
        });

        var fila = painel.fila();
        assertEquals(2, fila.size());

        ConversaResumo primeira = fila.getFirst();
        assertEquals(1L, primeira.id());
        assertEquals("Marina", primeira.clienteNome());
        assertEquals("+5581999999999", primeira.numeroTelefone());
        assertEquals("Assistente WXN", primeira.chatbotNome());
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, primeira.status());
        assertNotNull(primeira.dataInicio());

        // Cliente sem nome: o painel mostra "Nao disponivel" em vez de inventar.
        ConversaResumo segunda = fila.get(1);
        assertNull(segunda.clienteNome());
        assertEquals("+5581988887777", segunda.numeroTelefone());

        assertTrue(fila.stream().noneMatch(c -> "Cliente externo".equals(c.clienteNome())));
        assertTrue(fila.stream().allMatch(c -> c.status() == Conversa.Status.AGUARDANDO_ATENDENTE));
    }
}
