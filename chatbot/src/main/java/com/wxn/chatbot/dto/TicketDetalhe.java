package com.wxn.chatbot.dto;

import java.time.Instant;
import java.util.List;

import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;

// Detalhes de uma solicitacao: dados do ticket, do solicitante,
// da conversa de origem e o historico dessa conversa.
public record TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
        Long responsavelId, String responsavelNome, Solicitante solicitante,
        ConversaOrigem conversaOrigem, List<MensagemResumo> historico) {

    public TicketDetalhe {
        historico = List.copyOf(historico);
    }

    public record Solicitante(Long id, String nome, String email, String numeroTelefone,
            boolean identidadeConfirmada) {
    }

    public record ConversaOrigem(Long id, Conversa.Status status, Instant dataInicio,
            Instant dataEncerramento, String chatbotNome) {
    }
}
