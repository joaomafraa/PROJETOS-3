package com.wxn.chatbot.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Liga a operacao que pediu o registro (ex.: uma atualizacao do Telegram)
 * a solicitacao criada. Garante que repetir a operacao nao crie outra solicitacao.
 */
@Entity
@Table(name = "operacoes_solicitacao")
public class OperacaoSolicitacao implements Persistable<OperacaoSolicitacao.Chave> {
    public static final int TAMANHO_MAXIMO_CHAVE = 120;

    @EmbeddedId
    private Chave chave;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false, unique = true)
    private Ticket ticket;
    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    // A chave e informada por nos; sem isto o save faria "atualizar se existir" e uma
    // operacao concorrente com a mesma chave passaria sem erro. Novo registro = sempre insert.
    @Transient
    private boolean novo = true;

    protected OperacaoSolicitacao() {
    }

    @Override
    public Chave getId() { return chave; }

    @Override
    public boolean isNew() { return novo; }

    @PostLoad
    @PostPersist
    void marcarComoExistente() { novo = false; }

    public OperacaoSolicitacao(String chaveOperacao, Ticket ticket) {
        this.ticket = Objects.requireNonNull(ticket, "A solicitacao e obrigatoria.");
        this.chave = new Chave(ticket.getEmpresa().getId(), validarChave(chaveOperacao));
        this.criadaEm = Instant.now();
    }

    public static String validarChave(String chaveOperacao) {
        if (chaveOperacao == null || chaveOperacao.isBlank()) {
            throw new IllegalArgumentException("A chave da operacao e obrigatoria.");
        }
        String limpa = chaveOperacao.strip();
        if (limpa.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new IllegalArgumentException("A chave da operacao deve ter ate " + TAMANHO_MAXIMO_CHAVE + " caracteres.");
        }
        return limpa;
    }

    public Chave getChave() { return chave; }
    public Ticket getTicket() { return ticket; }
    public Instant getCriadaEm() { return criadaEm; }

    @Embeddable
    public static class Chave implements Serializable {
        @Column(name = "empresa_id", nullable = false, updatable = false)
        private Long empresaId;
        @Column(name = "chave_operacao", nullable = false, updatable = false, length = TAMANHO_MAXIMO_CHAVE)
        private String chaveOperacao;

        protected Chave() {
        }

        public Chave(Long empresaId, String chaveOperacao) {
            this.empresaId = Objects.requireNonNull(empresaId, "A empresa e obrigatoria.");
            this.chaveOperacao = Objects.requireNonNull(chaveOperacao, "A chave e obrigatoria.");
        }

        public Long getEmpresaId() { return empresaId; }
        public String getChaveOperacao() { return chaveOperacao; }

        @Override
        public boolean equals(Object outro) {
            return outro instanceof Chave chave
                    && empresaId.equals(chave.empresaId) && chaveOperacao.equals(chave.chaveOperacao);
        }

        @Override
        public int hashCode() {
            return Objects.hash(empresaId, chaveOperacao);
        }
    }
}
