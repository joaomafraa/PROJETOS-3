package com.wxn.chatbot.llm;

import java.util.List;

public interface ProvedorLlm {
    String responder(List<TrechoConversa> historico);

    // Fontes ja numeradas por ReferenciasResposta.numerar: fontes.get(i) recebe o rotulo F(i+1).
    default String responder(List<TrechoConversa> historico, List<FonteDisponivel> fontes) {
        if (fontes == null || fontes.isEmpty()) return responder(historico);
        throw new UnsupportedOperationException("Este provedor nao aceita fontes oficiais.");
    }

    record TrechoConversa(String autor, String conteudo) { }
}
