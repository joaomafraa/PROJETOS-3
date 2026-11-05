package com.wxn.chatbot.service;

import com.wxn.chatbot.dto.TicketAssumido;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssumirTicketService {
    private final TicketRepository tickets;
    private final EntityManager entityManager;

    public AssumirTicketService(TicketRepository tickets, EntityManager entityManager) {
        this.tickets = tickets;
        this.entityManager = entityManager;
    }

    /**
     * O atendenteId deve vir do contexto autenticado, nunca diretamente de uma
     * requisição pública. A integração com autenticação pertence à frente de acesso.
     */
    @Transactional
    public TicketAssumido assumir(Long ticketId, Long atendenteId) {
        validarId(ticketId, "ticket");
        validarId(atendenteId, "atendente");

        // Reutiliza o acesso JPA existente enquanto o repositório de atendentes
        // é desenvolvido na frente de administração.
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (atendente == null || !atendente.isAtivo()) {
            throw new SecurityException("É necessário um atendente cadastrado e ativo.");
        }

        Ticket ticket = tickets.buscarParaAtualizar(ticketId, atendente.getEmpresa().getId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket não encontrado nesta empresa."));

        if (ticket.getStatus() != Ticket.StatusTicket.PENDENTE) {
            throw new IllegalStateException("Somente um ticket pendente pode ser assumido.");
        }
        if (ticket.getResponsavel() != null
                && !ticket.getResponsavel().getId().equals(atendente.getId())) {
            throw new IllegalStateException("O ticket já está atribuído a outro atendente.");
        }

        ticket.atribuirResponsavel(atendente);
        ticket.iniciarAtendimento();
        // A entidade está gerenciada; o commit persiste as alterações e libera o bloqueio.
        return new TicketAssumido(ticket.getId(), ticket.getProtocolo(),
                ticket.getStatus(), ticket.getResponsavel().getId());
    }

    private static void validarId(Long id, String campo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O id de " + campo + " deve ser positivo.");
        }
    }
}
