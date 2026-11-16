package com.wxn.chatbot.security;

/** Entrega o codigo sem devolve-lo ao canal de atendimento. */
public interface EnvioCodigoCliente {
    void enviar(String email, String codigo);
}
