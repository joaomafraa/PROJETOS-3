package com.wxn.chatbot;
 
import java.util.Objects;
 
public class Chatbot {
 
    private final Long id;
    private final Empresa empresa;
    private String nome;
    private String mensagemBoasVindas;
    private boolean ativo;
 
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
}