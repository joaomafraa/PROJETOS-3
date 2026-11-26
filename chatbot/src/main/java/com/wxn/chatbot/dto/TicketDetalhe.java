package com.wxn.chatbot.dto;

import java.time.Instant;
import java.util.List;

import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;

// Detalhes de uma solicitacao: dados do ticket, do solicitante,
// da conversa de origem e o historico dessa conversa.
// preferenciaHorario: so em demonstracao, reuniao e visita (HU06.2); e preferencia, nao horario confirmado.
public record TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
        Long responsavelId, String responsavelNome, Solicitante solicitante,
        ConversaOrigem conversaOrigem, List<MensagemResumo> historico, String preferenciaHorario) {

    public TicketDetalhe {
        historico = List.copyOf(historico);
    }

    // Formato anterior, sem preferencia de horario.
    public TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
            Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
            Long responsavelId, String responsavelNome, Solicitante solicitante,
            ConversaOrigem conversaOrigem, List<MensagemResumo> historico) {
        this(id, protocolo, descricao, tipo, status, dataAbertura, dataConclusao, responsavelId, responsavelNome,
                solicitante, conversaOrigem, historico, null);
    }

    public record Solicitante(Long id, String nome, String email, String numeroTelefone,
            boolean identidadeConfirmada) {
    }

    public record ConversaOrigem(Long id, Conversa.Status status, Instant dataInicio,
            Instant dataEncerramento, String chatbotNome) {
    }
}
