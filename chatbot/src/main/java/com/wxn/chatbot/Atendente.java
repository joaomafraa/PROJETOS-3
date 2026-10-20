package com.wxn.chatbot;
 
import java.util.Objects;
import java.util.regex.Pattern;
 
public class Atendente {

    public enum PerfilAcesso{
        ADMINISTRADOR,
        COORDENADOR,
        ATENDENTE
    }

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Long id;
    private final Empresa empresa;
    private String nome;
    private String email;
    private PerfilAcesso perfil;
    private boolean ativo;

    public Atendente(Long id, String nome, String email, Empresa empresa, PerfilAcesso perfil) {
        this.id = Objects.requireNonNull(id, "O id do atendente e obrigatório.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id do atendente deve ser positivo.");
        }
        this.nome = validarNome(nome);
        this.email = validarEmail(email);
        this.empresa = Objects.requireNonNull(empresa, "A empresa do atendente e obrigatória.");
        this.perfil = Objects.requireNonNull(perfil, "O perfil de acesso e obrigatório.");
        this.ativo = true;
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
 
    public Empresa getEmpresa() {
        return empresa;
    }
 
    public PerfilAcesso getPerfil() {
        return perfil;
    }
 
    public boolean isAtivo() {
        return ativo;
    }
 
    public boolean pertenceA(Empresa outraEmpresa) {
        return outraEmpresa != null && empresa.getId().equals(outraEmpresa.getId());
    }
 
    public boolean possuiPerfil(PerfilAcesso perfilEsperado) {
        return perfil == perfilEsperado;
    }
 
    public void verificarPodeAtender(Empresa empresaDoAtendimento) {
        Objects.requireNonNull(empresaDoAtendimento, "A empresa do atendimento e obrigatória.");
        if (!ativo) {
            throw new IllegalStateException("O atendente está inativo.");
        }
        if (!pertenceA(empresaDoAtendimento)) {
            throw new IllegalArgumentException("O atendente não pertence a empresa do atendimento.");
        }
    }
}