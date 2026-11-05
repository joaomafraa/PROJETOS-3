package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.Ticket;

// Item da listagem de tickets.
public record TicketResumo(Long id, String protocolo, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, Instant dataAbertura, Long clienteId, String clienteNome,
        Long responsavelId, String responsavelNome) {
}
