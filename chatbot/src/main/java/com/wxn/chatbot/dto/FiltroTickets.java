package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Ticket;

// Filtros opcionais e combinaveis da listagem de tickets.
// Campos nulos nao filtram. semResponsavel lista apenas tickets sem atendente.
public record FiltroTickets(Ticket.TipoSolicitacao tipo, Ticket.StatusTicket status,
        Long responsavelId, boolean semResponsavel) {

    public FiltroTickets {
        if (responsavelId != null && responsavelId <= 0) {
            throw new IllegalArgumentException("O id do responsavel deve ser positivo.");
        }
        if (responsavelId != null && semResponsavel) {
            throw new IllegalArgumentException(
                    "Informe um responsavel ou peca tickets sem responsavel, nao os dois.");
        }
    }

    public static FiltroTickets nenhum() {
        return new FiltroTickets(null, null, null, false);
    }
}
