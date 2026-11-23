package com.wxn.chatbot.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public record DisponibilidadeAtendimento(
        Long empresaId,
        boolean configurada,
        boolean equipeHumanaDisponivel,
        ZoneId fuso,
        Optional<ZonedDateTime> proximaAbertura,
        List<String> assuntosAutomaticos,
        String mensagemForaExpediente) {

    public DisponibilidadeAtendimento {
        proximaAbertura = proximaAbertura == null ? Optional.empty() : proximaAbertura;
        assuntosAutomaticos = assuntosAutomaticos == null ? List.of() : List.copyOf(assuntosAutomaticos);
    }

    public boolean assuntoAtendidoAutomaticamente(String assunto) {
        if (assuntosAutomaticos.isEmpty()) {
            return true;
        }
        if (assunto == null || assunto.isBlank()) {
            return false;
        }
        return assuntosAutomaticos.stream().anyMatch(a -> a.equalsIgnoreCase(assunto.strip()));
    }
}
