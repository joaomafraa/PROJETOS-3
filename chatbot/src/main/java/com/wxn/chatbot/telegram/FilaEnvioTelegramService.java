package com.wxn.chatbot.telegram;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra o texto antes da tentativa de envio. Não repete automaticamente envios incertos. */
@Service
public class FilaEnvioTelegramService {
    private final EntityManager em;
    public FilaEnvioTelegramService(EntityManager em) { this.em = em; }

    @Transactional
    public void preparar(long empresaId, long atualizacaoId, long chatId, String conteudo) {
        if (empresaId <= 0 || atualizacaoId < 0 || chatId <= 0 || conteudo == null || conteudo.isBlank())
            throw new IllegalArgumentException("Dados de envio inválidos.");
        em.createNativeQuery("insert into telegram_envios (empresa_id, atualizacao_id, chat_id, conteudo, status) values (:empresa,:id,:chat,:conteudo,'PENDENTE')")
            .setParameter("empresa", empresaId).setParameter("id", atualizacaoId).setParameter("chat", chatId)
            .setParameter("conteudo", conteudo).executeUpdate();
    }

    @Transactional(readOnly = true)
    public String status(long empresaId, long atualizacaoId) {
        var lista = em.createNativeQuery("select status from telegram_envios where empresa_id = :empresa and atualizacao_id = :id")
            .setParameter("empresa", empresaId).setParameter("id", atualizacaoId).getResultList();
        return lista.isEmpty() ? null : (String) lista.get(0);
    }

    @Transactional
    public boolean marcarEnviado(long empresaId, long atualizacaoId) {
        return em.createNativeQuery("update telegram_envios set status = 'ENVIADO' where empresa_id = :empresa and atualizacao_id = :id and status = 'INCERTO'")
            .setParameter("empresa", empresaId).setParameter("id", atualizacaoId).executeUpdate() == 1;
    }

    @Transactional
    public boolean marcarIncerteza(long empresaId, long atualizacaoId) {
        return em.createNativeQuery("update telegram_envios set status = 'INCERTO' where empresa_id = :empresa and atualizacao_id = :id and status = 'PENDENTE'")
            .setParameter("empresa", empresaId).setParameter("id", atualizacaoId).executeUpdate() == 1;
    }
}
