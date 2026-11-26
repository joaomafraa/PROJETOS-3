package com.wxn.chatbot.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de seguranca da HU05.3: de tempos em tempos procura chamados das ultimas 24h
 * que ficaram sem nenhuma notificacao (falha no processamento ou responsavel
 * configurado depois) e gera os avisos que faltam. Desligado nos testes.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "nexus.notificacoes.reprocessamento.enabled", havingValue = "true", matchIfMissing = true)
public class NotificacaoChamadosAgendamento {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoChamadosAgendamento.class);
    private static final Duration JANELA = Duration.ofHours(24);

    private final NotificacaoChamadosOuvinte ouvinte;

    public NotificacaoChamadosAgendamento(NotificacaoChamadosOuvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    @Scheduled(initialDelayString = "${nexus.notificacoes.reprocessamento.atraso-inicial-ms:60000}",
            fixedDelayString = "${nexus.notificacoes.reprocessamento.intervalo-ms:300000}")
    public void reprocessar() {
        try {
            ouvinte.reprocessarPendentes(JANELA);
        } catch (RuntimeException e) {
            LOG.error("Reprocessamento de notificações falhou ({}).", e.getClass().getSimpleName());
        }
    }
}
