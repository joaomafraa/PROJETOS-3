package com.wxn.chatbot.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.*;

// Interessado registrado localmente (HU09.1). Nao envolve CRM nem o fluxo de Cliente.
@Entity
@Table(name = "leads")
public class Lead {

    public enum Origem {
        WHATSAPP,
        SITE,
        INDICACAO,
        EVENTO,
        OUTRO
    }

    public enum TipoContato {
        EMAIL,
        TELEFONE
    }

    public static final int TAMANHO_MAXIMO_NECESSIDADE = 500;

    private static final Pattern FORMATO_EMAIL = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leads_id_gen")
    @SequenceGenerator(name = "leads_id_gen", sequenceName = "leads_id_seq", allocationSize = 1)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;
    @Column(length = 255, updatable = false)
    private String nome;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contato", nullable = false, updatable = false, columnDefinition = "varchar(16)")
    private TipoContato tipoContato;
    @Column(nullable = false, updatable = false, length = 254)
    private String contato;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(32)")
    private Origem origem;
    @Column(nullable = false, updatable = false, length = TAMANHO_MAXIMO_NECESSIDADE)
    private String necessidade;
    @Column(name = "necessidade_chave", nullable = false, updatable = false,
            length = TAMANHO_MAXIMO_NECESSIDADE)
    private String necessidadeChave;
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    protected Lead() {
    }

    public Lead(Empresa empresa, String nome, String contato, Origem origem, String necessidade) {
        this.empresa = Objects.requireNonNull(empresa, "A empresa do lead é obrigatória.");
        this.origem = Objects.requireNonNull(origem, "A origem do lead é obrigatória.");
        this.nome = normalizarNome(nome);

        String contatoInformado = validarTexto(contato, "contato");
        if (contatoInformado.contains("@")) {
            this.tipoContato = TipoContato.EMAIL;
            this.contato = normalizarEmail(contatoInformado);
        } else {
            this.tipoContato = TipoContato.TELEFONE;
            this.contato = normalizarTelefone(contatoInformado);
        }

        this.necessidade = validarTexto(necessidade, "necessidade");
        if (this.necessidade.length() > TAMANHO_MAXIMO_NECESSIDADE) {
            throw new IllegalArgumentException(
                    "A necessidade deve ter no máximo " + TAMANHO_MAXIMO_NECESSIDADE + " caracteres.");
        }
        this.necessidadeChave = chaveDaNecessidade(this.necessidade);
        this.dataCriacao = Instant.now();
    }

    // Forma comparavel da necessidade: sem diferenca de caixa ou espacos repetidos.
    public static String chaveDaNecessidade(String necessidade) {
        return validarTexto(necessidade, "necessidade")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    // Mesma normalizacao aplicada no cadastro, para consultar por contato.
    public static String normalizarContato(String contato) {
        String informado = validarTexto(contato, "contato");
        return informado.contains("@") ? normalizarEmail(informado) : normalizarTelefone(informado);
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public String getNome() {
        return nome;
    }

    public TipoContato getTipoContato() {
        return tipoContato;
    }

    public String getContato() {
        return contato;
    }

    public Origem getOrigem() {
        return origem;
    }

    public String getNecessidade() {
        return necessidade;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    private static String normalizarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String normalizado = nome.strip();
        if (normalizado.length() > 255) {
            throw new IllegalArgumentException("O nome deve ter no máximo 255 caracteres.");
        }
        return normalizado;
    }

    private static String normalizarEmail(String email) {
        String normalizado = email.strip().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 254 || normalizado.indexOf('@') > 64
                || !FORMATO_EMAIL.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("O e-mail do lead é inválido.");
        }
        return normalizado;
    }

    private static String normalizarTelefone(String telefone) {
        String valor = telefone.strip();
        if (!valor.matches("\\+?[0-9 ()-]+")) {
            throw new IllegalArgumentException("O telefone do lead é inválido.");
        }
        valor = valor.replaceAll("[ ()-]", "");
        if (!valor.matches("(?:\\+[1-9][0-9]{6,14}|[0-9]{7,15})")) {
            throw new IllegalArgumentException("O telefone do lead é inválido.");
        }
        return valor;
    }

    private static String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " do lead é obrigatório.");
        }
        return valor.strip();
    }
}
