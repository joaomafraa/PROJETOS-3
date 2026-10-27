package com.wxn.chatbot.model;
 
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "atendentes")
public class Atendente {

    public enum PerfilAcesso {
        ADMINISTRADOR,
        COORDENADOR,
        ATENDENTE
    }

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private PerfilAcesso perfil;
    @Column(nullable = false)
    private boolean ativo;

    protected Atendente() {
    }

    public Atendente(Long id, String nome, String email, Empresa empresa, PerfilAcesso perfil) {
        this.id = Objects.requireNonNull(id, "O id do atendente é obrigatório.");
        if (id <= 0) {
            throw new IllegalArgumentException("O id do atendente deve ser positivo.");
        }
        this.nome = validarNome(nome);
        this.email = validarEmail(email);
        this.empresa = Objects.requireNonNull(empresa, "A empresa do atendente é obrigatória.");
        this.perfil = Objects.requireNonNull(perfil, "O perfil de acesso é obrigatório.");
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
        Objects.requireNonNull(empresaDoAtendimento, "A empresa do atendimento é obrigatória.");
        if (!ativo) {
            throw new IllegalStateException("O atendente está inativo.");
        }
        if (!pertenceA(empresaDoAtendimento)) {
            throw new IllegalArgumentException("O atendente não pertence à empresa do atendimento.");
        }
    }

    public void alterarNome(String nome) {
        this.nome = validarNome(nome);
    }
 
    public void alterarEmail(String email) {
        this.email = validarEmail(email);
    }
 
    public void alterarPerfil(PerfilAcesso novoPerfil, Atendente solicitante) {
        Objects.requireNonNull(novoPerfil, "O novo perfil de acesso é obrigatório.");
        verificarAdministradorDaMesmaEmpresa(solicitante);
        verificarNaoEhOProprioAtendente(solicitante, "Um administrador não pode alterar o próprio perfil.");
        this.perfil = novoPerfil;
    }

    public void desativar(Atendente solicitante) {
        verificarAdministradorDaMesmaEmpresa(solicitante);
        verificarNaoEhOProprioAtendente(solicitante, "Um administrador não pode desativar a si mesmo.");
        if (!ativo) {
            throw new IllegalStateException("O atendente já está inativo.");
        }
        this.ativo = false;
    }
 
    public void ativar(Atendente solicitante) {
        verificarAdministradorDaMesmaEmpresa(solicitante);
        if (ativo) {
            throw new IllegalStateException("O atendente já está ativo.");
        }
        this.ativo = true;
    }

    private void verificarAdministradorDaMesmaEmpresa(Atendente solicitante) {
        Objects.requireNonNull(solicitante, "O solicitante é obrigatório.");
        if (!solicitante.isAtivo() || !solicitante.possuiPerfil(PerfilAcesso.ADMINISTRADOR)) {
            throw new SecurityException("Apenas um administrador ativo pode realizar esta operação.");
        }
        if (!solicitante.pertenceA(empresa)) {
            throw new SecurityException("O administrador deve pertencer a mesma empresa do atendente.");
        }
    }
 
    private void verificarNaoEhOProprioAtendente(Atendente solicitante, String mensagem) {
        if (solicitante.getId().equals(id)) {
            throw new IllegalArgumentException(mensagem);
        }
    }
 
    private static String validarNome(String nome) {
        Objects.requireNonNull(nome, "O nome do atendente é obrigatório.");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("O nome do atendente não pode estar em branco.");
        }
        return nome.strip();
    }
 
    private static String validarEmail(String email) {
        Objects.requireNonNull(email, "O e-mail do atendente é obrigatório.");
        String emailNormalizado = email.strip().toLowerCase();
        if (!FORMATO_EMAIL.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException("O e-mail do atendente é inválido.");
        }
        return emailNormalizado;
    }
}
