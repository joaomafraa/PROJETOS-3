
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredencialAtendenteService {

    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final ContextoAcesso contextoAcesso;

    public CredencialAtendenteService(
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            ContextoAcesso contextoAcesso) {

        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.contextoAcesso = contextoAcesso;
    }

    @Transactional
    public void definirSenha(Long atendenteId, String novaSenha) {

        contextoAcesso.exigirAdministrador();

        if (novaSenha == null || novaSenha.length() < 8) {
            throw new IllegalArgumentException(
                    "A senha deve ter pelo menos 8 caracteres.");
        }

        Atendente atendente =
                entityManager.find(Atendente.class, atendenteId);

        if (atendente == null) {
            throw new IllegalArgumentException(
                    "Atendente inexistente.");
        }

        contextoAcesso.exigirMesmaEmpresa(atendente.getEmpresa());

        String hash = passwordEncoder.encode(novaSenha);

        CredencialAtendente credencial =
                entityManager.find(
                        CredencialAtendente.class,
                        atendenteId);

        if (credencial == null) {
            credencial = new CredencialAtendente(
                    atendenteId, hash);

            entityManager.persist(credencial);
        } else {
            credencial.alterarSenhaHash(hash);
        }
    }
}
