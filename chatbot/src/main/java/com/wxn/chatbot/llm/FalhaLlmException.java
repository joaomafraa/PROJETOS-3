package com.wxn.chatbot.llm;

/** Erro seguro para o chamador, sem divulgar credenciais ou detalhes do provedor. */
public class FalhaLlmException extends RuntimeException {
    public FalhaLlmException(String mensagem) {
        super(mensagem);
    }
}
