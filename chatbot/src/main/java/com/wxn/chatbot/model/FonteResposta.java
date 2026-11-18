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
import jakarta.persistence.Table;

import com.wxn.chatbot.llm.FonteDisponivel;

/**
 * Origem de uma resposta automatica salva (HU02.2).
 *
 * Registro imutavel: guarda uma copia dos dados do trecho para que a origem
 * continue consultavel mesmo apos desativacao ou nova versao do documento.
 */
@Entity
@Table(name = "fontes_resposta")
public class FonteResposta {
    public static final int TAMANHO_MAXIMO_EXCERTO = 600;

    public enum Uso {
        /** Trecho entregue a IA como contexto. Nao significa que sustenta a resposta. */
        RECUPERADA,
        /** Trecho referenciado explicitamente pela resposta. */
        CITADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false, updatable = false)
    private Conversa conversa;
    @Column(name = "mensagem_id", nullable = false, updatable = false)
    private Long mensagemId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(20)")
    private Uso uso;
    @Column(updatable = false)
    private Integer numero;
    @Column(nullable = false, updatable = false)
    private int ordem;
    @Column(name = "documento_id", nullable = false, updatable = false)
    private Long documentoId;
    @Column(name = "documento_versao", nullable = false, updatable = false, length = 64)
    private String documentoVersao;
    @Column(name = "documento_titulo", nullable = false, updatable = false, length = 200)
    private String documentoTitulo;
    @Column(name = "trecho_id", nullable = false, updatable = false, length = 100)
    private String trechoId;
    @Column(updatable = false, length = 200)
    private String localizacao;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String excerto;
    @Column(name = "registrada_em", nullable = false, updatable = false)
    private Instant registradaEm;

    protected FonteResposta() {
    }

    private FonteResposta(Conversa conversa, Mensagem mensagem, Uso uso, Integer numero, int ordem,
            FonteDisponivel fonte) {
        this.conversa = Objects.requireNonNull(conversa, "A conversa e obrigatoria.");
        Objects.requireNonNull(mensagem, "A mensagem e obrigatoria.");
        Objects.requireNonNull(fonte, "A fonte e obrigatoria.");
        if (!Objects.equals(mensagem.getConversa().getId(), conversa.getId())) {
            throw new IllegalArgumentException("A mensagem nao pertence a conversa informada.");
        }
        if (mensagem.getRemetente() != Mensagem.TipoRemetente.CHATBOT) {
            throw new IllegalArgumentException("Apenas respostas automaticas possuem fontes.");
        }
        if (!conversa.getEmpresa().getId().equals(fonte.empresaId())) {
            throw new IllegalArgumentException("A fonte pertence a outra empresa.");
        }
        if (ordem < 0) {
            throw new IllegalArgumentException("A ordem deve ser zero ou maior.");
        }
        this.empresa = conversa.getEmpresa();
        this.mensagemId = mensagem.getId();
        this.uso = Objects.requireNonNull(uso, "O uso da fonte e obrigatorio.");
        this.numero = numero;
        this.ordem = ordem;
        this.documentoId = fonte.documentoId();
        this.documentoVersao = limitar(fonte.documentoVersao(), 64);
        this.documentoTitulo = limitar(fonte.documentoTitulo(), 200);
        this.trechoId = limitar(fonte.trechoId(), 100);
        this.localizacao = fonte.localizacao() == null ? null : limitar(fonte.localizacao(), 200);
        this.excerto = limitar(fonte.texto(), TAMANHO_MAXIMO_EXCERTO);
        this.registradaEm = Instant.now();
    }

    public static FonteResposta recuperada(Conversa conversa, Mensagem mensagem, int ordem, FonteDisponivel fonte) {
        return new FonteResposta(conversa, mensagem, Uso.RECUPERADA, null, ordem, fonte);
    }

    public static FonteResposta citada(Conversa conversa, Mensagem mensagem, int numero, FonteDisponivel fonte) {
        if (numero < 1) {
            throw new IllegalArgumentException("O numero da citacao deve ser positivo.");
        }
        return new FonteResposta(conversa, mensagem, Uso.CITADA, numero, numero - 1, fonte);
    }

    private static String limitar(String valor, int maximo) {
        String limpo = valor.strip();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo - 1).strip() + "…";
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public Conversa getConversa() { return conversa; }
    public Long getMensagemId() { return mensagemId; }
    public Uso getUso() { return uso; }
    public Integer getNumero() { return numero; }
    public int getOrdem() { return ordem; }
    public Long getDocumentoId() { return documentoId; }
    public String getDocumentoVersao() { return documentoVersao; }
    public String getDocumentoTitulo() { return documentoTitulo; }
    public String getTrechoId() { return trechoId; }
    public String getLocalizacao() { return localizacao; }
    public String getExcerto() { return excerto; }
    public Instant getRegistradaEm() { return registradaEm; }
}
