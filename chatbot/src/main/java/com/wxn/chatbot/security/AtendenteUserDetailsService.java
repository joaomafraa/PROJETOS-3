
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;

import jakarta.persistence.EntityManager;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AtendenteUserDetailsService
        implements UserDetailsService {

    private final EntityManager entityManager;

    public AtendenteUserDetailsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {

        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException(
                    "Credenciais invalidas.");
        }

        String emailNormalizado =
                email.strip().toLowerCase(Locale.ROOT);

        var atendentes = entityManager.createQuery(
                "SELECT a FROM Atendente a " +
                "WHERE a.email = :email", Atendente.class)
                .setParameter("email", emailNormalizado)
                .setMaxResults(2)
                .getResultList();

        if (atendentes.size() != 1) {
            throw new UsernameNotFoundException(
                    "Credenciais invalidas.");
        }

        Atendente atendente = atendentes.get(0);

        CredencialAtendente credencial =
                entityManager.find(
                        CredencialAtendente.class,
                        atendente.getId());

        if (credencial == null) {
            throw new UsernameNotFoundException(
                    "Credenciais invalidas.");
        }

        return User.withUsername(atendente.getEmail())
                .password(credencial.getSenhaHash())
                .roles(atendente.getPerfil().name())
                .disabled(!atendente.isAtivo())
                .build();
    }
}
