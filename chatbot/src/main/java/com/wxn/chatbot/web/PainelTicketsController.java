package com.wxn.chatbot.web;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.dto.PaginaResumo;
import com.wxn.chatbot.dto.TicketDetalhe;
import com.wxn.chatbot.dto.TicketResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.service.ConsultaTicketsService;

// Consulta de solicitacoes no modo de apresentacao local.
// Usa o atendente configurado; nao substitui autenticacao em producao.
//
// GET /api/tickets?tipo=SUPORTE&status=PENDENTE&responsavelId=1&semResponsavel=false&pagina=0&tamanho=20
// GET /api/tickets/{ticketId}
@Profile("painel-local")
@RestController
@RequestMapping("/api/tickets")
@Transactional(readOnly = true)
public class PainelTicketsController {
    private final EntityManager entityManager;
    private final ConsultaTicketsService consulta;
    private final Long atendenteId;

    public PainelTicketsController(EntityManager entityManager, ConsultaTicketsService consulta,
            @Value("${painel.atendente-id:1}") Long atendenteId) {
        this.entityManager = entityManager;
        this.consulta = consulta;
        this.atendenteId = atendenteId;
    }

    @GetMapping
    public PaginaResumo<TicketResumo> listar(
            @RequestParam(required = false) Ticket.TipoSolicitacao tipo,
            @RequestParam(required = false) Ticket.StatusTicket status,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(defaultValue = "false") boolean semResponsavel,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        Atendente atendente = atendenteDoPainel();
        try {
            return consulta.listar(atendente,
                    new FiltroTickets(tipo, status, responsavelId, semResponsavel), pagina, tamanho);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/{ticketId}")
    public TicketDetalhe detalhar(@PathVariable Long ticketId) {
        Atendente atendente = atendenteDoPainel();
        try {
            return consulta.detalhar(ticketId, atendente);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket nao encontrado.");
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private Atendente atendenteDoPainel() {
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (atendente == null || !atendente.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Configure um atendente ativo para o painel local.");
        }
        return atendente;
    }
}
