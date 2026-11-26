package com.wxn.chatbot.service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.NotificacaoChamadoResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.NotificacaoChamado;
import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.AtendenteRepository;
import com.wxn.chatbot.repository.NotificacaoChamadoRepository;
import com.wxn.chatbot.repository.ResponsavelNotificacaoRepository;
import com.wxn.chatbot.repository.TicketRepository;

/**
 * Notificacoes de novos chamados (HU05.3).
 *
 * Quem recebe: os responsaveis configurados para NOVO_TICKET na empresa do chamado,
 * desde que estejam ativos. O canal e o proprio painel; nada e enviado por fora.
 */
@Service
public class NotificacaoChamadosService {

    private static final int LIMITE_DESCRICAO = 160;

    private final TicketRepository tickets;
    private final ResponsavelNotificacaoRepository responsaveis;
    private final NotificacaoChamadoRepository notificacoes;
    private final AtendenteRepository atendentes;

    public NotificacaoChamadosService(TicketRepository tickets, ResponsavelNotificacaoRepository responsaveis,
            NotificacaoChamadoRepository notificacoes, AtendenteRepository atendentes) {
        this.tickets = tickets;
        this.responsaveis = responsaveis;
        this.notificacoes = notificacoes;
        this.atendentes = atendentes;
    }

    /**
     * Resultado de um processamento.
     * semResponsaveis = a empresa nao tem nenhum responsavel ativo para novos chamados.
     */
    public record Resultado(Long ticketId, int criadas, int jaExistentes, boolean semResponsaveis) {
    }

    /**
     * Cria um aviso para cada responsavel elegivel. Pode ser chamado mais de uma vez
     * para o mesmo chamado: quem ja foi avisado nao recebe de novo.
     * Roda em transacao propria: se falhar, o chamado ja salvo nao e desfeito.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado notificarNovoChamado(Long ticketId) {
        if (ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("Chamado inválido.");
        }
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Chamado não encontrado."));
        Long empresaId = ticket.getEmpresa().getId();

        List<Atendente> elegiveis = responsaveis
                .findByEmpresaIdAndTipoOrderByAtendenteIdAsc(empresaId, TipoNotificacao.NOVO_TICKET)
                .stream()
                .map(ResponsavelNotificacao::getAtendente)
                .filter(Atendente::isAtivo)
                .filter(a -> a.pertenceA(ticket.getEmpresa()))
                .toList();
        if (elegiveis.isEmpty()) {
            return new Resultado(ticketId, 0, 0, true);
        }

        int criadas = 0;
        int jaExistentes = 0;
        for (Atendente destinatario : elegiveis) {
            if (notificacoes.existsByTicketIdAndDestinatarioIdAndTipo(ticketId, destinatario.getId(),
                    TipoNotificacao.NOVO_TICKET)) {
                jaExistentes++;
                continue;
            }
            notificacoes.save(new NotificacaoChamado(ticket, destinatario));
            criadas++;
        }
        return new Resultado(ticketId, criadas, jaExistentes, false);
    }

    // ---------- Painel: cada atendente so ve e altera os proprios avisos ----------

    @Transactional(readOnly = true)
    public List<NotificacaoChamadoResumo> listar(Long atendenteId, boolean somenteNaoLidas) {
        Atendente atendente = exigirAtendenteAtivo(atendenteId);
        Long empresaId = atendente.getEmpresa().getId();
        List<NotificacaoChamado> lista = somenteNaoLidas
                ? notificacoes.findTop50ByEmpresaIdAndDestinatarioIdAndLidaEmIsNullOrderByCriadaEmDescIdDesc(
                        empresaId, atendente.getId())
                : notificacoes.findTop50ByEmpresaIdAndDestinatarioIdOrderByCriadaEmDescIdDesc(
                        empresaId, atendente.getId());
        return lista.stream().map(NotificacaoChamadosService::resumir).toList();
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long atendenteId) {
        Atendente atendente = exigirAtendenteAtivo(atendenteId);
        return notificacoes.countByEmpresaIdAndDestinatarioIdAndLidaEmIsNull(
                atendente.getEmpresa().getId(), atendente.getId());
    }

    @Transactional
    public NotificacaoChamadoResumo marcarComoLida(Long atendenteId, Long notificacaoId) {
        Atendente atendente = exigirAtendenteAtivo(atendenteId);
        if (notificacaoId == null || notificacaoId <= 0) {
            throw new IllegalArgumentException("Notificação inválida.");
        }
        // Aviso de outra pessoa ou de outra empresa responde igual a um que nao existe.
        NotificacaoChamado notificacao = notificacoes
                .findByIdAndEmpresaIdAndDestinatarioId(notificacaoId, atendente.getEmpresa().getId(), atendente.getId())
                .orElseThrow(() -> new NoSuchElementException("Notificação não encontrada."));
        notificacao.marcarComoLida(atendente);
        return resumir(notificacao);
    }

    @Transactional
    public int marcarTodasComoLidas(Long atendenteId) {
        Atendente atendente = exigirAtendenteAtivo(atendenteId);
        return notificacoes.marcarTodasComoLidas(atendente.getEmpresa().getId(), atendente.getId(), Instant.now());
    }

    private Atendente exigirAtendenteAtivo(Long atendenteId) {
        if (atendenteId == null || atendenteId <= 0) {
            throw new SecurityException("Atendente não identificado.");
        }
        Atendente atendente = atendentes.findById(atendenteId)
                .orElseThrow(() -> new SecurityException("Atendente não identificado."));
        if (!atendente.isAtivo()) {
            throw new SecurityException("Atendente inativo.");
        }
        return atendente;
    }

    private static NotificacaoChamadoResumo resumir(NotificacaoChamado n) {
        Ticket t = n.getTicket();
        String descricao = t.getDescricao() == null ? "" : t.getDescricao().strip();
        if (descricao.length() > LIMITE_DESCRICAO) {
            descricao = descricao.substring(0, LIMITE_DESCRICAO - 1) + "…";
        }
        return new NotificacaoChamadoResumo(n.getId(), t.getId(), t.getProtocolo(), t.getTipo().name(),
                t.getStatus().name(), t.getCliente() == null ? null : t.getCliente().getNome(), descricao,
                n.getCriadaEm().toString(), n.isLida());
    }
}
