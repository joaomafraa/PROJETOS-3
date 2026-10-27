package com.wxn.chatbot;

import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "mensagens", uniqueConstraints = @UniqueConstraint(columnNames = {"conversa_id", "id"}))
public class Mensagem {

    public enum TipoRemetente {
        CLIENTE,
        CHATBOT,
        ATENDENTE
    }

    // Chave do banco; o id de dominio continua sendo unico dentro de cada conversa.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long codigo;
    @Column(nullable = false, updatable = false)
    private Long id;
    @Column(nullable = false, updatable = false)
    private int ordem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false, updatable = false)
    private Conversa conversa;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String conteudo;
    @Column(name = "data_envio", nullable = false, updatable = false)
    private Instant dataEnvio;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(20)")
    private TipoRemetente remetente;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id", updatable = false)
    private Atendente atendente;

    protected Mensagem() {
    }

    
    Mensagem(Long id, Conversa conversa, String conteudo, TipoRemetente remetente) {
        this(id, conversa, conteudo, remetente, null);
    }

    Mensagem(Long id, Conversa conversa, String conteudo, TipoRemetente remetente, Atendente atendente) {

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

        conversa.verificarEnvio(remetente, atendente);
        this.ordem = conversa.getMensagens().size();
        this.atendente = atendente;
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

    public Atendente getAtendente() {
        return atendente;
    }
}
