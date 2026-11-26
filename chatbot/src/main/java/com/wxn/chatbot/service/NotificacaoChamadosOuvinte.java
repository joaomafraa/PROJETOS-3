package com.wxn.chatbot.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wxn.chatbot.repository.NotificacaoChamadoRepository;

/**
 * Liga o registro do chamado as notificacoes (HU05.3).
 *
 * So age DEPOIS que o chamado foi gravado (AFTER_COMMIT). Se gerar os avisos falhar,
 * o chamado continua salvo e o reprocessamento tenta de novo mais tarde.
 */
@Component
public class NotificacaoChamadosOuvinte {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoChamadosOuvinte.class);

    private final NotificacaoChamadosService servico;
    private final NotificacaoChamadoRepository notificacoes;

    public NotificacaoChamadosOuvinte(NotificacaoChamadosService servico, NotificacaoChamadoRepository notificacoes) {
        this.servico = servico;
        this.notificacoes = notificacoes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoRegistrarSolicitacao(SolicitacaoRegistradaEvento evento) {
        try {
            NotificacaoChamadosService.Resultado r = servico.notificarNovoChamado(evento.ticketId());
            if (r.semResponsaveis()) {
                LOG.warn("Chamado {} da empresa {} registrado sem responsável ativo para novos chamados; ninguém foi notificado.",
                        evento.ticketId(), evento.empresaId());
            } else {
                LOG.info("Chamado {}: {} notificação(ões) criada(s), {} já existia(m).",
                        evento.ticketId(), r.criadas(), r.jaExistentes());
            }
        } catch (RuntimeException e) {
            // Sem mensagem da excecao no log: pode conter dados do banco.
            LOG.error("Falha ao gerar notificações do chamado {} ({}). O chamado continua salvo; haverá nova tentativa.",
                    evento.ticketId(), e.getClass().getSimpleName());
        }
    }

    /**
     * Tenta de novo para chamados recentes que ainda nao geraram nenhum aviso.
     * Devolve quantas notificacoes foram criadas agora.
     */
    public int reprocessarPendentes(Duration janela) {
        int criadas = 0;
        for (Long ticketId : notificacoes.chamadosSemNotificacaoDesde(Instant.now().minus(janela))) {
            try {
                criadas += servico.notificarNovoChamado(ticketId).criadas();
            } catch (RuntimeException e) {
                LOG.error("Reprocessamento: falha nas notificações do chamado {} ({}).",
                        ticketId, e.getClass().getSimpleName());
            }
        }
        if (criadas > 0) {
            LOG.info("Reprocessamento: {} notificação(ões) criada(s) para chamados pendentes.", criadas);
        }
        return criadas;
    }
}
