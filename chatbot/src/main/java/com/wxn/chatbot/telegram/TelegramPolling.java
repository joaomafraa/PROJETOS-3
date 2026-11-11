package com.wxn.chatbot.telegram;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Um único processo deve usar o token. Desativado até configuração explícita. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nexus.telegram.polling.enabled", havingValue = "true")
public class TelegramPolling {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramPolling.class);
    private final TelegramCliente telegram;
    private final ProcessarMensagemTelegramService processamento;
    private final long empresaId;
    private final long chatbotId;
    private long offset = 0;

    public TelegramPolling(TelegramCliente telegram, ProcessarMensagemTelegramService processamento,
            @Value("${nexus.telegram.empresa-id:0}") long empresaId,
            @Value("${nexus.telegram.chatbot-id:0}") long chatbotId) {
        if (empresaId <= 0 || chatbotId <= 0) throw new IllegalStateException("Configure empresa e chatbot do Telegram.");
        this.telegram = telegram; this.processamento = processamento; this.empresaId = empresaId; this.chatbotId = chatbotId;
    }

    @Scheduled(fixedDelayString = "${nexus.telegram.polling.intervalo-ms:5000}")
    public void consultar() {
        try {
            for (Object item : telegram.buscarAtualizacoes(offset)) {
                if (!(item instanceof Map<?, ?> dados) || !(dados.get("update_id") instanceof Number id))
                    throw new IllegalStateException("Atualização do Telegram inválida.");
                var mensagem = MensagemTelegram.interpretar(item);
                if (mensagem.isPresent()) processamento.processar(empresaId, chatbotId, mensagem.get());
                offset = Math.addExact(id.longValue(), 1);
            }
        } catch (RuntimeException erro) {
            // A próxima consulta retoma a mesma atualização; não registrar detalhes ou token.
            LOG.warn("Não foi possível processar atualização do Telegram; uma nova tentativa será feita.");
        }
    }
}
