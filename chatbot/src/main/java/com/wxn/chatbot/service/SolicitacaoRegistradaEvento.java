package com.wxn.chatbot.service;

/**
 * Publicado quando um chamado/solicitacao e realmente salvo (HU05.3).
 *
 * Contrato para quem cria tickets (Pedro, Guilherme): publique este evento dentro da
 * mesma transacao do save. As notificacoes so sao geradas depois do commit; se o
 * registro falhar, ninguem e avisado. Publicar de novo para o mesmo ticket nao duplica avisos.
 */
public record SolicitacaoRegistradaEvento(Long ticketId, Long empresaId) {

    public SolicitacaoRegistradaEvento {
        if (ticketId == null || ticketId <= 0 || empresaId == null || empresaId <= 0) {
            throw new IllegalArgumentException("Evento de solicitação sem chamado ou empresa válidos.");
        }
    }
}
