package com.wxn.chatbot.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

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
 * Contato para retorno de um pedido de orcamento (HU06.1).
 *
 * O pedido em si e um {@link Ticket} do tipo ORCAMENTO; aqui fica o contato que a equipe
 * comercial deve usar. Nao e uma proposta nem um preco: so o registro do que o cliente pediu.
 */
@Entity
@Table(name = "pedidos_orcamento")
public class PedidoOrcamento {
    public static final int TAMANHO_MAXIMO_CONTATO = 300;
    private static final int TAMANHO_MAXIMO_EMAIL = 254;
    private static final Pattern FORMATO_EMAIL = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

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
    @Column(name = "contato", nullable = false, length = TAMANHO_MAXIMO_CONTATO)
    private String contato;

    protected PedidoOrcamento() {
    }

    public PedidoOrcamento(Ticket ticket, String contato) {
        this.ticket = Objects.requireNonNull(ticket, "O pedido e obrigatorio.");
        if (ticket.getTipo() != Ticket.TipoSolicitacao.ORCAMENTO) {
            throw new IllegalArgumentException("Somente um pedido de orcamento tem contato de orcamento.");
        }
        this.empresa = ticket.getEmpresa();
        if (contato == null || contato.isBlank() || contato.strip().length() > TAMANHO_MAXIMO_CONTATO) {
            throw new IllegalArgumentException("O contato do pedido de orcamento e invalido.");
        }
        this.contato = contato.strip();
    }

    /** Contato que o cliente digitou: um e-mail ou um telefone validos, ja normalizados. */
    public static String contatoInformado(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Informe um e-mail ou telefone para retorno.");
        }
        String limpo = texto.strip();
        if (limpo.indexOf('@') >= 0) {
            String email = limpo.toLowerCase(Locale.ROOT);
            if (email.length() > TAMANHO_MAXIMO_EMAIL || email.indexOf('@') > 64
                    || !FORMATO_EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("O e-mail para retorno e invalido.");
            }
            return "E-mail: " + email;
        }
        try {
            return "Telefone: " + Cliente.normalizarTelefone(limpo);
        } catch (IllegalArgumentException erro) {
            throw new IllegalArgumentException("O telefone para retorno e invalido.");
        }
    }

    /**
     * Contato que o cliente ja deixou no cadastro (telefone e/ou e-mail). Vazio quando nao ha
     * nenhum: so nesse caso o canal precisa perguntar.
     */
    public static Optional<String> contatoDoCadastro(Cliente cliente) {
        Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        String telefone = cliente.getNumeroTelefone();
        String email = cliente.getEmail();
        boolean temTelefone = telefone != null && !telefone.isBlank();
        boolean temEmail = email != null && !email.isBlank();
        if (temTelefone && temEmail) {
            return Optional.of("Telefone: " + telefone + "; E-mail: " + email);
        }
        if (temTelefone) {
            return Optional.of("Telefone: " + telefone);
        }
        return temEmail ? Optional.of("E-mail: " + email) : Optional.empty();
    }

    public Long getTicketId() { return ticketId; }
    public Ticket getTicket() { return ticket; }
    public Empresa getEmpresa() { return empresa; }
    public String getContato() { return contato; }
}
