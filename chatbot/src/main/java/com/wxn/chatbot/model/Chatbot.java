package com.wxn.chatbot.model;
 
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chatbots")
public class Chatbot {
 
    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false)
    private String nome;
    @Column(name = "mensagem_boas_vindas", nullable = false)
    private String mensagemBoasVindas;
    private boolean ativo;

    protected Chatbot() {
    }
 
    public Chatbot(Long id, String nome, Empresa empresa, String mensagemBoasVindas) {
        this.id = Objects.requireNonNull(id, "O id do chatbot é obrigatório.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id do chatbot deve ser positivo.");
        }
        this.nome = validarNome(nome);
        this.empresa = Objects.requireNonNull(empresa, "A empresa do chatbot é obrigatória.");
        this.mensagemBoasVindas = validarMensagemBoasVindas(mensagemBoasVindas);
        this.ativo = true;
    }
 
    public Long getId() {
        return id;
    }
 
    public String getNome() {
        return nome;
    }
 
    public Empresa getEmpresa() {
        return empresa;
    }
 
    public String getMensagemBoasVindas() {
        return mensagemBoasVindas;
    }
 
    public boolean isAtivo() {
        return ativo;
    }
 
    public boolean pertenceA(Empresa outraEmpresa) {
        return outraEmpresa != null && empresa.getId().equals(outraEmpresa.getId());
    }
 
    public void verificarPodeAtender(Empresa empresaDoAtendimento) {
        Objects.requireNonNull(empresaDoAtendimento, "A empresa do atendimento é obrigatória.");
        if (!ativo) {
            throw new IllegalStateException("O chatbot está inativo.");
        }
        if (!pertenceA(empresaDoAtendimento)) {
            throw new IllegalArgumentException("O chatbot não pertence à empresa do atendimento.");
        }
    }

    public void alterarNome(String nome, Atendente solicitante) {
        String nomeValidado = validarNome(nome);
        verificarAdministradorDaMesmaEmpresa(solicitante);
        this.nome = nomeValidado;
    }
 
    public void alterarMensagemBoasVindas(String mensagemBoasVindas, Atendente solicitante) {
        String mensagemValidada = validarMensagemBoasVindas(mensagemBoasVindas);
        verificarAdministradorDaMesmaEmpresa(solicitante);
        this.mensagemBoasVindas = mensagemValidada;
    }
 
    public void desativar(Atendente solicitante) {
        verificarAdministradorDaMesmaEmpresa(solicitante);
        if (!ativo) {
            throw new IllegalStateException("O chatbot já está inativo.");
        }
        this.ativo = false;
    }
 
    public void ativar(Atendente solicitante) {
        verificarAdministradorDaMesmaEmpresa(solicitante);
        if (ativo) {
            throw new IllegalStateException("O chatbot já está ativo.");
        }
        this.ativo = true;
    }

    private void verificarAdministradorDaMesmaEmpresa(Atendente solicitante) {
        Objects.requireNonNull(solicitante, "O solicitante é obrigatório.");
        if (!solicitante.isAtivo() || !solicitante.possuiPerfil(Atendente.PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException("Apenas um administrador ativo pode configurar o chatbot.");
        }
        if (!solicitante.pertenceA(empresa)) {
            throw new SecurityException("O administrador deve pertencer à mesma empresa do chatbot.");
        }
    }
 
    private static String validarNome(String nome) {
        Objects.requireNonNull(nome, "O nome do chatbot é obrigatório.");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("O nome do chatbot não pode estar em branco.");
        }
        return nome.strip();
    }
 
    private static String validarMensagemBoasVindas(String mensagemBoasVindas) {
        Objects.requireNonNull(mensagemBoasVindas, "A mensagem de boas-vindas é obrigatória.");
        if (mensagemBoasVindas.isBlank()) {
            throw new IllegalArgumentException("A mensagem de boas-vindas não pode estar em branco.");
        }
        return mensagemBoasVindas.strip();
    }
}
