package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Ticket;

public record TicketAssumido(Long id, String protocolo, Ticket.StatusTicket status, Long responsavelId) {
}
