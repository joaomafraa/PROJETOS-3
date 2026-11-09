package com.wxn.chatbot.service;

public class AtendenteDuplicadoException extends RuntimeException {

    public AtendenteDuplicadoException(String mensagem) {
        super(mensagem);
    }

    public AtendenteDuplicadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
