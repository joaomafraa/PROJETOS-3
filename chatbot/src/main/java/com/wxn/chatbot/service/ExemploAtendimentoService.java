package com.wxn.chatbot.service;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

@Service
@Profile("painel-local")
public class ExemploAtendimentoService {
    private static final Long ID_EXEMPLO = 900000001L;
    private final EntityManager entityManager;
    private final ConversaRepository conversas;
    private final TransactionTemplate transacao;
    private final Long atendenteId;

    public ExemploAtendimentoService(EntityManager entityManager, ConversaRepository conversas,
            PlatformTransactionManager transactionManager,
            @Value("${painel.atendente-id:1}") Long atendenteId) {
        this.entityManager = entityManager;
        this.conversas = conversas;
        this.transacao = new TransactionTemplate(transactionManager);
        this.atendenteId = atendenteId;
    }

    // Serializa os cliques nesta instancia local ate o commit da transacao.
    public synchronized Long criar() {
        return transacao.execute(status -> criarOuConsultar());
    }

    private Long criarOuConsultar() {
        Conversa existente = entityManager.find(Conversa.class, ID_EXEMPLO);
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (existente != null) {
            if (atendente == null || !atendente.isAtivo()
                    || !atendente.pertenceA(existente.getEmpresa())
                    || !"marina.exemplo@example.com".equals(existente.getCliente().getEmail())
                    || !"Assistente de exemplo".equals(existente.getChatbot().getNome())) {
                throw new IllegalStateException("O identificador do exemplo ja esta em uso ou o atendente esta indisponivel.");
            }
            return existente.getId();
        }
        if (entityManager.find(Cliente.class, ID_EXEMPLO) != null
                || entityManager.find(Chatbot.class, ID_EXEMPLO) != null) {
            throw new IllegalStateException("Os identificadores do exemplo ja estao em uso.");
        }
        Empresa empresa;
        if (atendente == null) {
            if (entityManager.find(Empresa.class, ID_EXEMPLO) != null) {
                throw new IllegalStateException("O identificador da empresa de exemplo ja esta em uso.");
            }
            empresa = new Empresa(ID_EXEMPLO, "WXN - Demonstracao");
            entityManager.persist(empresa);
            atendente = new Atendente(atendenteId, "Ana (exemplo)", "ana.exemplo@example.com",
                    empresa, Atendente.PerfilAcesso.ATENDENTE);
            entityManager.persist(atendente);
        } else {
            if (!atendente.isAtivo()) {
                throw new IllegalStateException("O atendente do painel esta inativo.");
            }
            empresa = atendente.getEmpresa();
        }
        Cliente cliente = new Cliente(ID_EXEMPLO, "+55 81 99999-0000");
        cliente.alterarNome("Marina (exemplo)");
        cliente.atualizarEmail("marina.exemplo@example.com");
        cliente.informarEmpresa("Empresa ficticia");
        cliente.vincularEmpresa(empresa);
        entityManager.persist(cliente);
        Chatbot chatbot = new Chatbot(ID_EXEMPLO, "Assistente de exemplo", empresa,
                "Ola, Marina! Vou encaminhar seu pedido para nossa equipe.");
        entityManager.persist(chatbot);
        Conversa conversa = new Conversa(ID_EXEMPLO, cliente, empresa, chatbot);
        conversa.adicionarMensagem(1L, "Gostaria de agendar uma demonstracao do sistema.", Mensagem.TipoRemetente.CLIENTE);
        conversa.adicionarMensagem(2L, chatbot.getMensagemBoasVindas(), Mensagem.TipoRemetente.CHATBOT);
        conversa.solicitarAtendimentoHumano();
        conversa.iniciarAtendimentoHumano(atendente);
        conversa.adicionarMensagem(3L, "Ola! Sou do atendimento e vou ajudar com o agendamento.",
                Mensagem.TipoRemetente.ATENDENTE, atendente);
        conversa.adicionarMensagem(4L, "Obrigada! Podemos combinar para amanha?", Mensagem.TipoRemetente.CLIENTE);
        conversas.saveAndFlush(conversa);
        return conversa.getId();
    }
}
