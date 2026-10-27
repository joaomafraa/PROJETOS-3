package com.wxn.chatbot;

import java.time.Instant;

// Dados da fila prontos para uso, sem depender de uma sessao JPA aberta.
public record ConversaResumo(Long id, Long clienteId, String clienteNome,
        String numeroTelefone, String chatbotNome, Instant dataInicio, Conversa.Status status) {
}
