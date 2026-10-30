package com.wxn.chatbot.model;

import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.*;

@Entity
@Table(name = "tickets")
public class Ticket {

    public enum TipoSolicitacao {
        SUPORTE,
        ORCAMENTO,
        DEMONSTRACAO,
        REUNIAO,
        VISITA_TECNICA
    }

    public enum StatusTicket {
        PENDENTE,
        EM_ATENDIMENTO,
        CONCLUIDO
    }

    @Id
    private Long id;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String protocolo;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String descricao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(32)")
    private TipoSolicitacao tipo;
    @Column(name = "data_abertura", nullable = false, updatable = false)
    private Instant dataAbertura;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_origem_id", nullable = false, updatable = false)
    private Conversa conversaOrigem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private StatusTicket status;
    @Column(name = "data_conclusao")
    private Instant dataConclusao;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Atendente responsavel;

    protected Ticket() {
    }

    public Ticket(
            Long id,
            String protocolo,
            String descricao,
            TipoSolicitacao tipo,
            Conversa conversaOrigem) {

        this.id = Objects.requireNonNull(
                id, "O id do ticket é obrigatório.");

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "O id do ticket deve ser positivo.");
        }

        this.protocolo = validarTexto(protocolo, "protocolo");
        this.descricao = validarTexto(descricao, "descrição");

        this.tipo = Objects.requireNonNull(
                tipo, "O tipo da solicitação é obrigatório.");

        this.conversaOrigem = Objects.requireNonNull(
                conversaOrigem, "A conversa de origem é obrigatória.");

        this.cliente = conversaOrigem.getCliente();

        this.empresa = conversaOrigem.getEmpresa();
        this.dataAbertura = Instant.now();
        this.status = StatusTicket.PENDENTE;
    }

    public Long getId() {
        return id;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoSolicitacao getTipo() {
        return tipo;
    }

    public StatusTicket getStatus() {
        return status;
    }

    public Instant getDataAbertura() {
        return dataAbertura;
    }

    public Instant getDataConclusao() {
        return dataConclusao;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Conversa getConversaOrigem() {
        return conversaOrigem;
    }

    public Atendente getResponsavel() {
        return responsavel;
    }

    public void atribuirResponsavel(Atendente atendente) {
        verificarNaoConcluido();

        Objects.requireNonNull(
                atendente, "O atendente responsável é obrigatório.");

        atendente.verificarPodeAtender(empresa);

        this.responsavel = atendente;
    }

    public void iniciarAtendimento() {
        if (status != StatusTicket.PENDENTE) {
            throw new IllegalStateException(
                    "O ticket precisa estar pendente para iniciar o atendimento.");
        }

        verificarResponsavel();

        this.status = StatusTicket.EM_ATENDIMENTO;
    }

    public void concluir() {
        if (status != StatusTicket.EM_ATENDIMENTO) {
            throw new IllegalStateException(
                    "O ticket precisa estar em atendimento para ser concluído.");
        }

        verificarResponsavel();

        this.dataConclusao = Instant.now();
        this.status = StatusTicket.CONCLUIDO;
    }

    private void verificarResponsavel() {
        if (responsavel == null) {
            throw new IllegalStateException(
                    "O ticket precisa ter um atendente responsável.");
        }

        responsavel.verificarPodeAtender(empresa);
    }

    private void verificarNaoConcluido() {
        if (status == StatusTicket.CONCLUIDO) {
            throw new IllegalStateException(
                    "O ticket já foi concluído.");
        }
    }

    private static String validarTexto(String valor, String campo) {
        Objects.requireNonNull(
                valor, "O campo " + campo + " é obrigatório.");

        if (valor.isBlank()) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " não pode estar em branco.");
        }

        return valor.strip();
    }
}
