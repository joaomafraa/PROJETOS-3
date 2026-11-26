package com.wxn.chatbot.model;

import java.time.Instant;
import java.util.Objects;

import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;

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
 * Aviso de novo chamado para um responsavel da empresa (HU05.3).
 *
 * Fica salvo e aparece no painel do destinatario. Nao representa um envio externo:
 * "criada" quer dizer disponivel no painel; "lida" quer dizer que o proprio destinatario abriu.
 */
@Entity
@Table(name = "notificacoes_chamados")
public class NotificacaoChamado {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notificacoes_chamados_id_gen")
    @SequenceGenerator(name = "notificacoes_chamados_id_gen",
            sequenceName = "notificacoes_chamados_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atendente_id", nullable = false, updatable = false)
    private Atendente destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(40)")
    private TipoNotificacao tipo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "lida_em")
    private Instant lidaEm;

    protected NotificacaoChamado() {
    }

    /** Cria o aviso de novo chamado. Destinatario e chamado precisam ser da mesma empresa. */
    public NotificacaoChamado(Ticket ticket, Atendente destinatario) {
        this.ticket = Objects.requireNonNull(ticket, "O chamado é obrigatório.");
        this.destinatario = Objects.requireNonNull(destinatario, "O destinatário é obrigatório.");
        Empresa empresaDoChamado = Objects.requireNonNull(ticket.getEmpresa(), "O chamado não tem empresa.");
        if (!destinatario.pertenceA(empresaDoChamado)) {
            throw new IllegalArgumentException("O destinatário não pertence à empresa do chamado.");
        }
        if (!destinatario.isAtivo()) {
            throw new IllegalStateException("Atendente inativo não recebe notificações.");
        }
        this.empresa = empresaDoChamado;
        this.tipo = TipoNotificacao.NOVO_TICKET;
        this.criadaEm = Instant.now();
    }

    /** So o proprio destinatario marca como lida. Marcar de novo nao muda a data. */
    public void marcarComoLida(Atendente leitor) {
        Objects.requireNonNull(leitor, "Informe quem leu a notificação.");
        if (!Objects.equals(leitor.getId(), destinatario.getId())) {
            throw new IllegalArgumentException("Só o destinatário pode marcar a notificação como lida.");
        }
        if (lidaEm == null) {
            lidaEm = Instant.now();
        }
    }

    public boolean isLida() {
        return lidaEm != null;
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Atendente getDestinatario() {
        return destinatario;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getLidaEm() {
        return lidaEm;
    }
}
