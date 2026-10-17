package com.wxn.chatbot;
public class Cliente {
    private Long id;
    private String nome;
    private String email;
    private String numeroTelefone;
    private boolean identidadeConfirmada;
    private Empresa empresa;

    public Cliente(String numeroTelefone){
        this.numeroTelefone = numeroTelefone;
        this.identidadeConfirmada = false;
    }
    
    
    public Long getId(){
        return id;
    }

    public String getNome(){
        return nome;
    }

    public void setNome(String nome){
        this.nome = nome;
    }

    public String getEmail(){
        return email;
    }

    public void atualizarEmail(String email){
        this.email = email;
        this.identidadeConfirmada = false;
    }

    public String getNumeroTelefone(){
        return numeroTelefone;
    }

    public void atualizarTelefone(String numeroTelefone){
        this.numeroTelefone = numeroTelefone;
        this.identidadeConfirmada = false;
    }

    public boolean isIdentidadeConfirmada(){
        return identidadeConfirmada;
    }

    public void confirmarIdentidade(){
        this.identidadeConfirmada = true;
    }

    public Empresa getEmpresa(){
        return empresa;
    }

    public void trocarEmpresa(Empresa empresa){    // so o admin vai poder chamar
        this.empresa = empresa;
    }
}