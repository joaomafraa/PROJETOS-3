package com.wxn.chatbot.telegram;
/** Diagnóstico sem conteúdo de mensagens, identificadores ou segredos. */
public class FalhaEtapaTelegramException extends RuntimeException {
 private final String etapa;
 public FalhaEtapaTelegramException(String etapa, RuntimeException causa) { super("Falha no processamento do Telegram.", causa); this.etapa=etapa; }
 public String etapa() { return etapa; }
}
