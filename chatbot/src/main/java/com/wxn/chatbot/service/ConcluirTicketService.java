package com.wxn.chatbot.service;

import com.wxn.chatbot.dto.TicketConcluido;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConcluirTicketService {
    private final TicketRepository tickets;
    private final EntityManager entityManager;

    public ConcluirTicketService(TicketRepository tickets, EntityManager entityManager) {
        this.tickets = tickets;
        this.entityManager = entityManager;
    }

    /** O atendenteId deve vir do contexto autenticado, não de uma requisição pública. */
    @Transactional
    public TicketConcluido concluir(Long ticketId, Long atendenteId) {
        validarId(ticketId, "ticket");
        validarId(atendenteId, "atendente");
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (atendente == null || !atendente.isAtivo()) {
            throw new SecurityException("É necessário um atendente cadastrado e ativo.");
        }
        Ticket ticket = tickets.buscarParaAtualizar(ticketId, atendente.getEmpresa().getId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket não encontrado nesta empresa."));
        if (ticket.getStatus() != Ticket.StatusTicket.EM_ATENDIMENTO) {
            throw new IllegalStateException("Somente um ticket em atendimento pode ser concluído.");
        }
        if (ticket.getResponsavel() == null
                || !ticket.getResponsavel().getId().equals(atendente.getId())) {
            throw new SecurityException("Somente o responsável pelo ticket pode concluí-lo.");
        }
        ticket.concluir();
        return new TicketConcluido(ticket.getId(), ticket.getProtocolo(), ticket.getStatus(),
                ticket.getResponsavel().getId(), ticket.getDataConclusao());
    }

    private static void validarId(Long id, String campo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O id de " + campo + " deve ser positivo.");
        }
    }
}
