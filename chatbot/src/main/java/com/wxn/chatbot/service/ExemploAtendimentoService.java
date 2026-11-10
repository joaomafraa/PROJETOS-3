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

@Service
@Profile("painel-local")
public class ExemploAtendimentoService {
    private static final long BASE = 900000010L;
    private final EntityManager entityManager;
    private final TransactionTemplate transacao;
    private final Long atendenteId;

    public ExemploAtendimentoService(EntityManager entityManager, PlatformTransactionManager transactionManager,
            @Value("${painel.atendente-id:1}") Long atendenteId) {
        this.entityManager = entityManager;
        this.transacao = new TransactionTemplate(transactionManager);
        this.atendenteId = atendenteId;
    }

    // Serializa os cliques nesta instancia local ate o commit da transacao.
    public synchronized Long criar() {
        return transacao.execute(status -> {
            Atendente atendente = entityManager.find(Atendente.class, atendenteId);
            if (atendente == null) {
                if (entityManager.find(Empresa.class, BASE) != null) {
                    throw new IllegalStateException("O identificador da empresa de exemplo ja esta em uso.");
                }
                Empresa empresa = new Empresa(BASE, "WXN - Demonstracao");
                entityManager.persist(empresa);
                atendente = new Atendente(atendenteId, "Ana (exemplo)", "ana.exemplo@example.com",
                        empresa, Atendente.PerfilAcesso.ATENDENTE);
                entityManager.persist(atendente);
            } else if (!atendente.isAtivo()) {
                throw new IllegalStateException("O atendente do painel esta inativo.");
            }

            Empresa empresa = atendente.getEmpresa();
            Empresa outra = entityManager.find(Empresa.class, BASE + 1);
            if (outra == null) {
                outra = new Empresa(BASE + 1, "WXN - Outra empresa de demonstracao");
                entityManager.persist(outra);
            } else if (!"WXN - Outra empresa de demonstracao".equals(outra.getNome())
                    || outra.getId().equals(empresa.getId())) {
                throw new IllegalStateException("O identificador da outra empresa de exemplo ja esta em uso.");
            }

            criarConversa(BASE, empresa, "Marina", Conversa.Status.AGUARDANDO_ATENDENTE, atendente);
            criarConversa(BASE + 1, empresa, "Lucas", Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, atendente);
            criarConversa(BASE + 2, empresa, "Carla", Conversa.Status.EM_ATENDIMENTO_HUMANO, atendente);
            criarConversa(BASE + 3, outra, "Rafael", Conversa.Status.AGUARDANDO_ATENDENTE, null);
            return BASE;
        });
    }

    private void criarConversa(long id, Empresa empresa, String nome, Conversa.Status status, Atendente atendente) {
        String email = nome.toLowerCase(java.util.Locale.ROOT) + ".demo@example.com";
        String nomeChatbot = "Assistente de demonstracao " + id;
        Conversa existente = entityManager.find(Conversa.class, id);
        if (existente != null) {
            if (!existente.getEmpresa().getId().equals(empresa.getId())
                    || !email.equals(existente.getCliente().getEmail())
                    || !nomeChatbot.equals(existente.getChatbot().getNome())) {
                throw new IllegalStateException("O identificador da conversa de exemplo ja esta em uso.");
            }
            return;
        }
        if (entityManager.find(Cliente.class, id) != null || entityManager.find(Chatbot.class, id) != null) {
            throw new IllegalStateException("Os identificadores do exemplo ja estao em uso.");
        }

        Cliente cliente = new Cliente(id, "+55 81 99999-000" + (id - BASE));
        cliente.alterarNome(nome + " (exemplo)");
        cliente.atualizarEmail(email);
        cliente.vincularEmpresa(empresa);
        entityManager.persist(cliente);
        Chatbot chatbot = new Chatbot(id, nomeChatbot, empresa, "Ola! Como posso ajudar?");
        entityManager.persist(chatbot);
        Conversa conversa = new Conversa(id, cliente, empresa, chatbot);
        conversa.adicionarMensagem(1L, "Gostaria de informacoes sobre o atendimento.", Mensagem.TipoRemetente.CLIENTE);
        conversa.adicionarMensagem(2L, chatbot.getMensagemBoasVindas(), Mensagem.TipoRemetente.CHATBOT);
        if (status != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            conversa.solicitarAtendimentoHumano();
        }
        if (status == Conversa.Status.EM_ATENDIMENTO_HUMANO) {
            conversa.iniciarAtendimentoHumano(atendente);
            conversa.adicionarMensagem(3L, "Ola! Vou ajudar voce.", Mensagem.TipoRemetente.ATENDENTE, atendente);
        }
        entityManager.persist(conversa);
    }
}
