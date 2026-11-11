
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredencialAtendenteServiceTests {

    private EntityManager entityManager;
    private PasswordEncoder passwordEncoder;
    private ContextoAcesso contextoAcesso;
    private CredencialAtendenteService service;

    @BeforeEach
    void configurar() {
        entityManager = mock(EntityManager.class);
        passwordEncoder = new BCryptPasswordEncoder();
        contextoAcesso = mock(ContextoAcesso.class);

        service = new CredencialAtendenteService(
                entityManager,
                passwordEncoder,
                contextoAcesso);
    }

    @Test
    void deveCriarCredencialComSenhaCriptografada() {

        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente atendente = new Atendente(
                1L, "Gabriel", "gabriel@teste.com",
                empresa, Atendente.PerfilAcesso.ATENDENTE);

        when(entityManager.find(Atendente.class, 1L))
                .thenReturn(atendente);

        when(entityManager.find(
                CredencialAtendente.class, 1L))
                .thenReturn(null);

        service.definirSenha(1L, "Senha123");

        var captor = org.mockito.ArgumentCaptor.forClass(
                CredencialAtendente.class);

        verify(entityManager).persist(captor.capture());

        CredencialAtendente credencial = captor.getValue();

        assertEquals(1L, credencial.getAtendenteId());
        assertNotEquals("Senha123",
                credencial.getSenhaHash());

        assertTrue(passwordEncoder.matches(
                "Senha123",
                credencial.getSenhaHash()));
    }

    @Test
    void deveRejeitarSenhaCurta() {

        assertThrows(IllegalArgumentException.class,
                () -> service.definirSenha(1L, "1234567"));

        verify(entityManager, never())
                .persist(any());
    }

    @Test
    void deveRejeitarAtendenteInexistente() {

        when(entityManager.find(Atendente.class, 99L))
                .thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.definirSenha(
                        99L, "Senha123"));
    }

    @Test
    void deveBloquearUsuarioSemPermissaoAdministrativa() {

        doThrow(new AccessDeniedException(
                "Operacao exige administrador."))
                .when(contextoAcesso)
                .exigirAdministrador();

        assertThrows(AccessDeniedException.class,
                () -> service.definirSenha(
                        1L, "Senha123"));

        verify(entityManager, never())
                .persist(any());
    }

    @Test
    void deveBloquearAlteracaoDeOutraEmpresa() {

        Empresa empresa = new Empresa(2L, "Empresa B");

        Atendente atendente = new Atendente(
                2L, "Pedro", "pedro@teste.com",
                empresa, Atendente.PerfilAcesso.ATENDENTE);

        when(entityManager.find(Atendente.class, 2L))
                .thenReturn(atendente);

        doThrow(new AccessDeniedException(
                "Empresa nao autorizada."))
                .when(contextoAcesso)
                .exigirMesmaEmpresa(empresa);

        assertThrows(AccessDeniedException.class,
                () -> service.definirSenha(
                        2L, "Senha123"));

        verify(entityManager, never())
                .persist(any());
    }
}
