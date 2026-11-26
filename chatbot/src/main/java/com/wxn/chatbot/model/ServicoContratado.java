package com.wxn.chatbot.model;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.*;

// HU04.1: plano ou servico ficticio vinculado a um cliente da empresa (dados de demonstracao).
@Entity
@Table(name = "servicos_contratados")
public class ServicoContratado {

    public enum Tipo { PLANO, SERVICO }

    public enum Situacao { ATIVO, SUSPENSO, ENCERRADO }

    public static final int TAMANHO_MAXIMO_NOME = 120;
    public static final int TAMANHO_MAXIMO_DETALHE = 300;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Cliente cliente;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Tipo tipo;
    @Column(nullable = false, length = TAMANHO_MAXIMO_NOME) private String nome;
    @Column(length = TAMANHO_MAXIMO_DETALHE) private String detalhe;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Situacao situacao;
    @Column(nullable = false) private LocalDate inicio;
    private LocalDate fim;
    @Column(nullable = false) private boolean demonstracao = true;

    protected ServicoContratado() { }

    public ServicoContratado(Cliente cliente, Tipo tipo, String nome, String detalhe, Situacao situacao,
            LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        if (cliente.getEmpresa() == null) throw new IllegalArgumentException("O cliente precisa pertencer a uma empresa.");
        this.cliente = cliente;
        this.empresa = cliente.getEmpresa();
        this.tipo = Objects.requireNonNull(tipo, "O tipo e obrigatorio.");
        this.nome = texto(nome, TAMANHO_MAXIMO_NOME, "nome");
        this.detalhe = detalhe == null || detalhe.isBlank() ? null : texto(detalhe, TAMANHO_MAXIMO_DETALHE, "detalhe");
        this.situacao = Objects.requireNonNull(situacao, "A situacao e obrigatoria.");
        this.inicio = Objects.requireNonNull(inicio, "A data de inicio e obrigatoria.");
        if (fim != null && fim.isBefore(inicio)) throw new IllegalArgumentException("O fim nao pode ser antes do inicio.");
        this.fim = fim;
    }

    private static String texto(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        String limpo = valor.strip();
        if (limpo.length() > maximo) throw new IllegalArgumentException("O " + campo + " excede " + maximo + " caracteres.");
        return limpo;
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public Cliente getCliente() { return cliente; }
    public Tipo getTipo() { return tipo; }
    public String getNome() { return nome; }
    public String getDetalhe() { return detalhe; }
    public Situacao getSituacao() { return situacao; }
    public LocalDate getInicio() { return inicio; }
    public LocalDate getFim() { return fim; }
    public boolean isDemonstracao() { return demonstracao; }
}
