package com.wxn.chatbot;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresas")
public class Empresa {
    @Id
    private Long id;
    @Column(nullable = false)
    private String nome;

    protected Empresa() {
    }

    public Empresa(Long id, String nome){
        this.id = Objects.requireNonNull(id, "O id da mepresa e obrigatorio.");

        if (id <= 0){
            throw new IllegalArgumentException("O id da empresa deve ser positivo");
        }

        validarNome(nome);
        this.nome = nome;
    }

    public Long getId(){
        return id;
    }

    public String getNome(){
        return nome;
    }

    public void alterarNome(String nome){
        validarNome(nome);
        this.nome = nome;
    }

    public void validarNome(String nome){
        Objects.requireNonNull(nome, "O nome da empresa e obrigatorio.");

        if (nome.isBlank()) {
            throw new IllegalArgumentException("O nome da empresa nao pode estar em branco.");
        }
    }
}
