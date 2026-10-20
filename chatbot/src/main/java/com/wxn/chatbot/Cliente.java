package com.wxn.chatbot;

public class Cliente {

    private final Long id;
    private String nome;
    private String email;
    private String numeroTelefone;
    private boolean identidadeConfirmada;
    private Empresa empresa;

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

    public void alterarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente não pode estar vazio.");
        }
        this.nome = nome.trim();
    }

    public void atualizarEmail(String email) {
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("O e-mail do cliente é inválido.");
        }
        this.email = email.trim().toLowerCase();
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

    // Primeiro vínculo, feito pelo chat no cadastro.
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
        String telefone = numeroTelefone.replace(" ", "").replace("-", "");
        if (telefone.length() < 10) {
            throw new IllegalArgumentException("O telefone do cliente é inválido.");
        }
        return telefone;
    }
}