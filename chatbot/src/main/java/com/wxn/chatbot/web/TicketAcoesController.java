package com.wxn.chatbot.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.TicketAssumido;
import com.wxn.chatbot.dto.TicketConcluido;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.AssumirTicketService;
import com.wxn.chatbot.service.ConcluirTicketService;

// Alteracao do andamento de uma solicitacao (HU10.3). O atendente vem sempre da
// sessao autenticada; a requisicao so informa qual ticket sera alterado.
//
// POST /api/tickets/{ticketId}/assumir   -> 200 TicketAssumido
// POST /api/tickets/{ticketId}/concluir  -> 200 TicketConcluido
//
// Erros: 401 sem sessao, 403 sem permissao ou atendente inativo, 404 ticket inexistente
// (ou de outra empresa), 409 estado incompativel (ex.: ja assumido por outro atendente).
@RestController
@RequestMapping("/api/tickets")
public class TicketAcoesController {
    private final AssumirTicketService assumirTicket;
    private final ConcluirTicketService concluirTicket;
    private final ContextoAcesso contextoAcesso;

    public TicketAcoesController(AssumirTicketService assumirTicket,
            ConcluirTicketService concluirTicket, ContextoAcesso contextoAcesso) {
        this.assumirTicket = assumirTicket;
        this.concluirTicket = concluirTicket;
        this.contextoAcesso = contextoAcesso;
    }

    @PostMapping("/{ticketId}/assumir")
    public TicketAssumido assumir(@PathVariable Long ticketId) {
        Long atendenteId = contextoAcesso.atendenteIdAtual();
        try {
            return assumirTicket.assumir(ticketId, atendenteId);
        } catch (IllegalArgumentException e) {
            throw naoEncontrado();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping("/{ticketId}/concluir")
    public TicketConcluido concluir(@PathVariable Long ticketId) {
        Long atendenteId = contextoAcesso.atendenteIdAtual();
        try {
            return concluirTicket.concluir(ticketId, atendenteId);
        } catch (IllegalArgumentException e) {
            throw naoEncontrado();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket nao encontrado.");
    }
}
