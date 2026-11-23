package com.wxn.chatbot.dto;

import java.util.List;

public record SalvarConfiguracaoAtendimentoComando(
        String fuso,
        String mensagemForaExpediente,
        List<ConfiguracaoAtendimentoResumo.Horario> horarios,
        List<String> assuntosAutomaticos) {
}
