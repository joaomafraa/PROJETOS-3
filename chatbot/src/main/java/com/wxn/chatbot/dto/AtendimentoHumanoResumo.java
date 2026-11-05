package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Conversa;

public record AtendimentoHumanoResumo(Long conversaId, Conversa.Status status, Long responsavelId) {
}
