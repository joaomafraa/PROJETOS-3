package com.wxn.chatbot.security;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Atendente;

import jakarta.persistence.EntityManager;

/**
 * Provisiona somente a primeira credencial, sem expor uma rota publica.
 * O administrador precisa existir previamente no cadastro de atendentes.
 */
@Component
@ConditionalOnProperty(name = "wxn.security.bootstrap.enabled", havingValue = "true")
public class CredencialAdministradorBootstrap implements ApplicationRunner {

    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public CredencialAdministradorBootstrap(
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            @Value("${wxn.security.bootstrap.admin-email:}") String email,
            @Value("${wxn.security.bootstrap.admin-password:}") String senha) {
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long quantidade = entityManager.createQuery(
                "SELECT COUNT(c) FROM CredencialAtendente c", Long.class)
                .getSingleResult();

        // Nunca sobrescreve credenciais nem funciona como redefinicao de senha.
        if (quantidade > 0) {
            return;
        }

        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "Informe o e-mail do administrador para o bootstrap de seguranca.");
        }
        if (senha == null || senha.length() < 8) {
            throw new IllegalStateException(
                    "A senha inicial do administrador deve ter pelo menos 8 caracteres.");
        }

        var administradores = entityManager.createQuery(
                "SELECT a FROM Atendente a WHERE a.email = :email", Atendente.class)
                .setParameter("email", email.strip().toLowerCase(Locale.ROOT))
                .setMaxResults(2)
                .getResultList();

        if (administradores.size() != 1) {
            throw new IllegalStateException(
                    "O administrador informado nao existe ou possui e-mail duplicado.");
        }

        Atendente administrador = administradores.getFirst();
        if (!administrador.isAtivo()
                || administrador.getPerfil() != Atendente.PerfilAcesso.ADMINISTRADOR) {
            throw new IllegalStateException(
                    "A primeira credencial exige um administrador ativo.");
        }

        entityManager.persist(new CredencialAtendente(
                administrador.getId(), passwordEncoder.encode(senha)));
    }
}
