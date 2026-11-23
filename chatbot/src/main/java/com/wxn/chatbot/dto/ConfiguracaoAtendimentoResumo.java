package com.wxn.chatbot.dto;

import java.util.List;

public record ConfiguracaoAtendimentoResumo(
        boolean configurada,
        String fuso,
        String mensagemForaExpediente,
        List<Horario> horarios,
        List<String> assuntosAutomaticos,
        String atualizadoEm,
        boolean equipeDisponivelAgora,
        String proximaAbertura) {

    public record Horario(String diaSemana, String abertura, String fechamento) {
    }
}
