package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.ResponsavelNotificacao;

public record ResponsavelNotificacaoResumo(ResponsavelNotificacao.TipoNotificacao tipo, Long atendenteId,
        String atendenteNome, String atendenteEmail, boolean atendenteAtivo, Instant dataConfiguracao) {

    public static ResponsavelNotificacaoResumo de(ResponsavelNotificacao responsavel) {
        var atendente = responsavel.getAtendente();
        return new ResponsavelNotificacaoResumo(responsavel.getTipo(), atendente.getId(),
                atendente.getNome(), atendente.getEmail(), atendente.isAtivo(),
                responsavel.getDataConfiguracao());
    }
}
