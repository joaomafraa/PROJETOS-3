package com.wxn.chatbot.dto;

import java.time.Instant;
import com.wxn.chatbot.model.Ticket;

public record TicketConcluido(Long id, String protocolo, Ticket.StatusTicket status,
        Long responsavelId, Instant dataConclusao) {
}
