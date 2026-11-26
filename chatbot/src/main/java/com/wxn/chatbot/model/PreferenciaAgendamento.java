package com.wxn.chatbot.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Preferencia de horario de um pedido de demonstracao, reuniao ou visita tecnica (HU06.2).
 *
 * Guarda o texto informado pelo cliente (ex.: "terca a tarde"). Nao e um agendamento:
 * a data e o horario ainda precisam ser confirmados pela equipe.
 */
@Entity
@Table(name = "preferencias_agendamento")
public class PreferenciaAgendamento {
    public static final int TAMANHO_MINIMO = 3;
    public static final int TAMANHO_MAXIMO = 300;
    public static final Set<Ticket.TipoSolicitacao> TIPOS_COM_PREFERENCIA = EnumSet.of(
            Ticket.TipoSolicitacao.DEMONSTRACAO, Ticket.TipoSolicitacao.REUNIAO, Ticket.TipoSolicitacao.VISITA_TECNICA);

    @Id
    @Column(name = "ticket_id")
    private Long ticketId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @Column(name = "preferencia_horario", nullable = false, length = TAMANHO_MAXIMO)
    private String preferenciaHorario;

    protected PreferenciaAgendamento() {
    }

    public PreferenciaAgendamento(Ticket ticket, String preferenciaHorario) {
        this.ticket = Objects.requireNonNull(ticket, "O pedido e obrigatorio.");
        if (!TIPOS_COM_PREFERENCIA.contains(ticket.getTipo())) {
            throw new IllegalArgumentException("Somente demonstracao, reuniao e visita tecnica tem preferencia de horario.");
        }
        this.empresa = ticket.getEmpresa();
        this.preferenciaHorario = validar(preferenciaHorario);
    }

    public static String validar(String preferenciaHorario) {
        if (preferenciaHorario == null) {
            throw new IllegalArgumentException("Informe a preferencia de horario.");
        }
        String limpa = preferenciaHorario.replaceAll("\\s+", " ").strip();
        if (limpa.length() < TAMANHO_MINIMO || limpa.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("A preferencia de horario deve ter de "
                    + TAMANHO_MINIMO + " a " + TAMANHO_MAXIMO + " caracteres.");
        }
        return limpa;
    }

    public Long getTicketId() { return ticketId; }
    public Ticket getTicket() { return ticket; }
    public Empresa getEmpresa() { return empresa; }
    public String getPreferenciaHorario() { return preferenciaHorario; }
}
