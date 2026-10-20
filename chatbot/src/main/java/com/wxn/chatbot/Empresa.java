package com.wxn.chatbot;

import java.util.Objects;

public class Empresa {
    private final Long id;
    private String nome;

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

    public void validarNome(String nome){
        Objects.requireNonNull(nome, "O nome da empresa e obrigatorio.");

        if (nome.isBlank()) {
            throw new IllegalArgumentException("O nome da empresa nao pode estar em branco.");
        }
    }
}
