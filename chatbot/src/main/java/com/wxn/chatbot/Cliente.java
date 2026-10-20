package com.wxn.chatbot;

import java.util.Locale;
import java.util.regex.Pattern;

public class Cliente {

    private static final Pattern FORMATO_EMAIL = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private final Long id;
    private String nome;
    private String email;
    private String numeroTelefone;
    private boolean identidadeConfirmada;
    private Empresa empresa;
    private String empresaInformada;

    public Cliente(Long id, String numeroTelefone) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O id do cliente deve ser positivo.");
        }
        this.id = id;
        this.numeroTelefone = validarTelefone(numeroTelefone);
        this.identidadeConfirmada = false;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getNumeroTelefone() {
        return numeroTelefone;
    }

    public boolean isIdentidadeConfirmada() {
        return identidadeConfirmada;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public String getEmpresaInformada() {
        return empresaInformada;
    }

    // Declaracao do interessado; nao concede acesso nem cria vinculo com um ambiente.
    public void informarEmpresa(String nomeEmpresa) {
        if (nomeEmpresa == null || nomeEmpresa.isBlank()) {
            throw new IllegalArgumentException("O nome da empresa informada e obrigatorio.");
        }
        this.empresaInformada = nomeEmpresa.strip();
    }

    public void alterarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente não pode estar vazio.");
        }
        this.nome = nome.trim();
    }

    public void atualizarEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("O e-mail do cliente é inválido.");
        }
        String emailNormalizado = email.strip().toLowerCase(Locale.ROOT);
        if (emailNormalizado.length() > 254 || emailNormalizado.indexOf('@') > 64
                || !FORMATO_EMAIL.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException("O e-mail do cliente é inválido.");
        }
        this.email = emailNormalizado;
        this.identidadeConfirmada = false;
    }

    public void atualizarTelefone(String numeroTelefone) {
        this.numeroTelefone = validarTelefone(numeroTelefone);
        this.identidadeConfirmada = false;
    }

    public void confirmarIdentidade() {
        if (email == null) {
            throw new IllegalStateException("O cliente precisa ter e-mail para confirmar a identidade.");
        }
        this.identidadeConfirmada = true;
    }

    // Vinculo de dominio definido pelo cadastro confiavel, nunca pelo texto informado no chat.
    public void vincularEmpresa(Empresa empresa) {
        if (empresa == null) {
            throw new IllegalArgumentException("A empresa é obrigatória.");
        }
        if (this.empresa != null) {
            throw new IllegalStateException("O cliente já tem empresa. Use trocarEmpresa.");
        }
        this.empresa = empresa;
    }

    // Troca de empresa: só um administrador ativo da empresa atual do cliente pode fazer.
    public void trocarEmpresa(Empresa novaEmpresa, Atendente solicitante) {
        if (novaEmpresa == null || solicitante == null) {
            throw new IllegalArgumentException("A nova empresa e o solicitante são obrigatórios.");
        }
        if (this.empresa == null) {
            throw new IllegalStateException("O cliente ainda não tem empresa. Use vincularEmpresa.");
        }
        if (!solicitante.isAtivo() || !solicitante.possuiPerfil(Atendente.PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException("Apenas um administrador ativo pode trocar a empresa.");
        }
        if (!solicitante.pertenceA(this.empresa)) {
            throw new SecurityException("O administrador deve ser da empresa atual do cliente.");
        }
        if (this.empresa.getId().equals(novaEmpresa.getId())) {
            throw new IllegalArgumentException("O cliente já pertence a essa empresa.");
        }
        this.empresa = novaEmpresa;
    }

    private String validarTelefone(String numeroTelefone) {
        if (numeroTelefone == null) {
            throw new IllegalArgumentException("O telefone do cliente é obrigatório.");
        }
        String telefone = numeroTelefone.strip();
        if (!telefone.matches("\\+?[0-9 ()-]+")) {
            throw new IllegalArgumentException("O telefone do cliente é inválido.");
        }
        telefone = telefone.replaceAll("[ ()-]", "");
        // Aceita numeros locais e internacionais sem presumir um codigo de pais.
        if (!telefone.matches("(?:\\+[1-9][0-9]{6,14}|[0-9]{7,15})")) {
            throw new IllegalArgumentException("O telefone do cliente é inválido.");
        }
        return telefone;
    }
}
