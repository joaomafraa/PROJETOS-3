package com.wxn.chatbot.service;

// Já existe atendente com o mesmo e-mail na empresa.
public class AtendenteDuplicadoException extends RuntimeException {

    public AtendenteDuplicadoException(String mensagem) {
        super(mensagem);
    }

    public AtendenteDuplicadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
