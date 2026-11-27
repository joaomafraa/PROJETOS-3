package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.Ticket;

/**
 * Pedido de orcamento ja salvo (HU06.1).
 *
 * - So existe depois do registro no banco; se o registro falhar, nao ha resultado e nada deve ser confirmado.
 * - E o registro de um pedido: nao traz preco, prazo nem proposta. Quem responde e a equipe comercial.
 * - novo e falso quando a mesma operacao foi reprocessada: o pedido e o protocolo sao os da
 *   primeira vez e nada foi criado de novo.
 */
public record PedidoOrcamentoRegistrado(Long id, String protocolo, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, String necessidade, String contato, Instant dataAbertura,
        Long empresaId, Long clienteId, Long conversaId, boolean novo) {
}
