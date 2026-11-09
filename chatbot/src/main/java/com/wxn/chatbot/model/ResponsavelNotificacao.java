package com.wxn.chatbot.model;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Atendente configurado para receber um tipo de notificacao da empresa (HU08.3).
 *
 * Apenas registra o destinatario; o envio de notificacoes nao faz parte desta entrega.
 */
@Entity
@Table(name = "responsaveis_notificacao")
public class ResponsavelNotificacao {

    public enum TipoNotificacao {
        NOVO_TICKET,
        ATENDIMENTO_HUMANO_SOLICITADO,
        NOVO_LEAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "responsaveis_notificacao_id_gen")
    @SequenceGenerator(name = "responsaveis_notificacao_id_gen",
            sequenceName = "responsaveis_notificacao_id_seq", allocationSize = 1)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atendente_id", nullable = false, updatable = false)
    private Atendente atendente;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(40)")
    private TipoNotificacao tipo;
    @Column(name = "data_configuracao", nullable = false, updatable = false)
    private Instant dataConfiguracao;

    protected ResponsavelNotificacao() {
    }

    public ResponsavelNotificacao(Atendente atendente, TipoNotificacao tipo) {
        this.atendente = Objects.requireNonNull(atendente, "O atendente responsável é obrigatório.");
        this.tipo = Objects.requireNonNull(tipo, "O tipo de notificação é obrigatório.");
        if (!atendente.isAtivo()) {
            throw new IllegalStateException("Um atendente inativo não pode ser responsável por notificações.");
        }
        this.empresa = atendente.getEmpresa();
        this.dataConfiguracao = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public Atendente getAtendente() {
        return atendente;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public Instant getDataConfiguracao() {
        return dataConfiguracao;
    }
}
