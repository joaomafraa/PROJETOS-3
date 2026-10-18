package com.wxn.chatbot;

import java.time.Instant;
import java.util.Objects;

public class Mensagem {

    public enum TipoRemetente {
        CLIENTE,
        CHATBOT,
        ATENDENTE
    }

    private final Long id;
    private final Conversa conversa;
    private final String conteudo;
    private final Instant dataEnvio;
    private final TipoRemetente remetente;

    Mensagem(Long id, Conversa conversa, String conteudo, TipoRemetente remetente) {
        this.id = Objects.requireNonNull(id, "O id da mensagem e obrigatorio.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id da mensagem deve ser positivo.");
        }
        this.conversa = Objects.requireNonNull(conversa, "A conversa e obrigatoria.");
        this.conteudo = Objects.requireNonNull(conteudo, "O conteudo e obrigatorio.");
        if (conteudo.isBlank()) {
            throw new IllegalArgumentException("O conteudo da mensagem nao pode estar em branco.");
        }
        this.remetente = Objects.requireNonNull(remetente, "O remetente e obrigatorio.");
        this.dataEnvio = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversa getConversa() {
        return conversa;
    }

    public String getConteudo() {
        return conteudo;
    }

    public Instant getDataEnvio() {
        return dataEnvio;
    }

    public TipoRemetente getRemetente() {
        return remetente;
    }
}
