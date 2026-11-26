package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.Ticket;

/**
 * Pedido de demonstracao, reuniao ou visita tecnica ja salvo (HU06.2).
 *
 * Contrato para a confirmacao ao cliente (HU06.3):
 * - so existe depois do registro no banco; se o registro falhar, nao ha resultado;
 * - preferenciaHorario e o que o cliente pediu, nao um horario confirmado;
 * - novo e falso quando a mesma operacao foi reprocessada: o pedido e o protocolo
 *   sao os mesmos da primeira vez e nada foi criado de novo.
 */
public record PedidoAgendamentoRegistrado(Long id, String protocolo, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, String necessidade, String preferenciaHorario, Instant dataAbertura,
        Long empresaId, Long clienteId, Long conversaId, boolean novo) {

    /** Sempre verdadeiro nesta entrega: data e horario dependem de confirmacao da equipe. */
    public boolean horarioAConfirmar() {
        return true;
    }
}
