package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Mensagem;

import java.time.Instant;

public record MensagemResumo(Long id, String conteudo, Instant dataEnvio,
        Mensagem.TipoRemetente remetente, Long atendenteId, String autor) {
}
