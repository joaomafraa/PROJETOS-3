package com.wxn.chatbot.repository;

import org.springframework.data.jpa.domain.Specification;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.model.Ticket;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> daEmpresaComFiltro(Long empresaId, FiltroTickets filtro) {
        Specification<Ticket> criterio = daEmpresa(empresaId);
        if (filtro.tipo() != null) {
            criterio = criterio.and(doTipo(filtro.tipo()));
        }
        if (filtro.status() != null) {
            criterio = criterio.and(comStatus(filtro.status()));
        }
        if (filtro.responsavelId() != null) {
            criterio = criterio.and(doResponsavel(filtro.responsavelId()));
        }
        if (filtro.semResponsavel()) {
            criterio = criterio.and(semResponsavel());
        }
        return criterio;
    }

    static Specification<Ticket> daEmpresa(Long empresaId) {
        return (ticket, consulta, cb) -> cb.equal(ticket.get("empresa").get("id"), empresaId);
    }

    static Specification<Ticket> doTipo(Ticket.TipoSolicitacao tipo) {
        return (ticket, consulta, cb) -> cb.equal(ticket.get("tipo"), tipo);
    }

    static Specification<Ticket> comStatus(Ticket.StatusTicket status) {
        return (ticket, consulta, cb) -> cb.equal(ticket.get("status"), status);
    }

    static Specification<Ticket> doResponsavel(Long responsavelId) {
        return (ticket, consulta, cb) -> cb.equal(ticket.get("responsavel").get("id"), responsavelId);
    }

    static Specification<Ticket> semResponsavel() {
        return (ticket, consulta, cb) -> cb.isNull(ticket.get("responsavel"));
    }
}
