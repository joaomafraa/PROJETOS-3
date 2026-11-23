package com.wxn.chatbot.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documentos_conhecimento")
public class DocumentoConhecimento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id") private Empresa empresa;
    @Column(nullable = false, length = 200) private String titulo;
    @Column(nullable = false, columnDefinition = "text") private String conteudo;
    @Column(nullable = false) private int versao;
    @Column(nullable = false) private boolean ativo;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;

    protected DocumentoConhecimento() { }
    public DocumentoConhecimento(Empresa empresa, String titulo, String conteudo) {
        this.empresa = java.util.Objects.requireNonNull(empresa);
        this.titulo = validarTitulo(titulo);
        this.conteudo = validarConteudo(conteudo);
        this.versao = 1;
        this.ativo = true;
        this.criadoEm = Instant.now();
    }
    public void atualizar(String titulo, String conteudo) {
        String novoTitulo = validarTitulo(titulo);
        String novoConteudo = validarConteudo(conteudo);
        if (!this.titulo.equals(novoTitulo) || !this.conteudo.equals(novoConteudo)) {
            this.titulo = novoTitulo; this.conteudo = novoConteudo; this.versao++;
        }
    }
    private static String validarTitulo(String valor) {
        if (valor == null || valor.isBlank() || valor.strip().length() > 200) throw new IllegalArgumentException("Informe um título de até 200 caracteres.");
        return valor.strip();
    }
    private static String validarConteudo(String valor) {
        if (valor == null || valor.isBlank() || valor.length() > 200000 || valor.indexOf('\0') >= 0) throw new IllegalArgumentException("O conteúdo deve ter entre 1 e 200.000 caracteres válidos.");
        return valor.strip();
    }
    public void definirAtivo(boolean ativo) { this.ativo = ativo; }
    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public String getTitulo() { return titulo; }
    public String getConteudo() { return conteudo; }
    public int getVersao() { return versao; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
}
