package com.wxn.chatbot.llm;

import java.util.List;

/** Contrato independente do provedor para facilitar testes e substituição. */
public interface ProvedorLlm {
    String responder(List<TrechoConversa> historico);

    record TrechoConversa(String autor, String conteudo) { }
}
