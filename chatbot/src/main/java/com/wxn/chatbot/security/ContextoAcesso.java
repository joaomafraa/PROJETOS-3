
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Component
public class ContextoAcesso {

    private final EntityManager entityManager;

    public ContextoAcesso(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Atendente atendenteAtual() {

        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException(
                    "Atendente nao autenticado.");
        }

        String email = auth.getName();

        if (email == null || email.isBlank()) {
            throw new AccessDeniedException(
                    "Identidade invalida.");
        }

        List<Atendente> atendentes = entityManager.createQuery(
                "SELECT a FROM Atendente a " +
                "WHERE a.email = :email", Atendente.class)
                .setParameter(
                        "email",
                        email.strip().toLowerCase(Locale.ROOT))
                .setMaxResults(2)
                .getResultList();

        if (atendentes.size() != 1) {
            throw new AccessDeniedException(
                    "Identidade invalida.");
        }

        Atendente atendente = atendentes.get(0);

        if (!atendente.isAtivo()) {
            throw new AccessDeniedException(
                    "Atendente inativo.");
        }

        return atendente;
    }

    public Long atendenteIdAtual() {
        return atendenteAtual().getId();
    }

    public Long empresaIdAtual() {
        return atendenteAtual().getEmpresa().getId();
    }

    public Atendente.PerfilAcesso perfilAtual() {
        return atendenteAtual().getPerfil();
    }

    public void exigirMesmaEmpresa(Empresa empresa) {

        if (empresa == null
                || !empresaIdAtual().equals(empresa.getId())) {
            throw new AccessDeniedException(
                    "Acesso a outra empresa negado.");
        }
    }

    public void exigirAdministrador() {

        if (perfilAtual()
                != Atendente.PerfilAcesso.ADMINISTRADOR) {
            throw new AccessDeniedException(
                    "Operacao exige administrador.");
        }
    }
}
