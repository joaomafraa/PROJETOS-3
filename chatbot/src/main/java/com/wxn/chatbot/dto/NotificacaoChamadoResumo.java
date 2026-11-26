package com.wxn.chatbot.dto;

/** O que o painel recebe de cada aviso de novo chamado (HU05.3). */
public record NotificacaoChamadoResumo(
        Long id,
        Long ticketId,
        String protocolo,
        String tipoSolicitacao,
        String statusChamado,
        String clienteNome,
        String descricao,
        String criadaEm,
        boolean lida) {
}
