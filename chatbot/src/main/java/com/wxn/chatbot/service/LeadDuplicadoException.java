package com.wxn.chatbot.service;

// Mesmo contato com a mesma necessidade ja registrado na empresa.
public class LeadDuplicadoException extends RuntimeException {

    public LeadDuplicadoException(String mensagem) {
        super(mensagem);
    }

    public LeadDuplicadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
