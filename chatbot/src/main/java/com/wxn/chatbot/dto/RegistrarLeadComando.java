package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Lead;

// Entrada do registro de lead. O nome e opcional; os demais campos sao obrigatorios.
public record RegistrarLeadComando(String nome, String contato, Lead.Origem origem, String necessidade) {
}
