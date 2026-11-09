package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.Ticket;

public record TicketAberto(
        Long id,
        String protocolo,
        String descricao,
        Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status,
        Instant dataAbertura,
        Long empresaId,
        Long clienteId,
        Long conversaId) {
}
