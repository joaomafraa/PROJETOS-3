package com.wxn.chatbot.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

class CredencialAdministradorBootstrapTests {

    private EntityManager entityManager;
    private TypedQuery<Long> contagem;
    private TypedQuery<Atendente> buscaAtendente;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void configurar() {
        entityManager = mock(EntityManager.class);
        contagem = mock(TypedQuery.class);
        buscaAtendente = mock(TypedQuery.class);
        encoder = new BCryptPasswordEncoder();

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(contagem);
        when(entityManager.createQuery(anyString(), eq(Atendente.class)))
                .thenReturn(buscaAtendente);
        when(buscaAtendente.setParameter(eq("email"), anyString()))
                .thenReturn(buscaAtendente);
        when(buscaAtendente.setMaxResults(2)).thenReturn(buscaAtendente);
    }

    @Test
    void criaPrimeiraCredencialParaAdministradorAtivoComBCrypt() {
        Atendente administrador = new Atendente(7L, "Admin", "admin@example.com",
                new Empresa(1L, "WXN"), Atendente.PerfilAcesso.ADMINISTRADOR);
        when(contagem.getSingleResult()).thenReturn(0L);
        when(buscaAtendente.getResultList()).thenReturn(List.of(administrador));

        new CredencialAdministradorBootstrap(entityManager, encoder,
                " ADMIN@EXAMPLE.COM ", "SenhaInicial123!").run(null);

        ArgumentCaptor<CredencialAtendente> captor =
                ArgumentCaptor.forClass(CredencialAtendente.class);
        verify(entityManager).persist(captor.capture());
        assertTrue(encoder.matches("SenhaInicial123!", captor.getValue().getSenhaHash()));
    }

    @Test
    void naoSobrescreveCredenciaisQuandoBootstrapPermaneceHabilitado() {
        when(contagem.getSingleResult()).thenReturn(1L);

        new CredencialAdministradorBootstrap(entityManager, encoder,
                "admin@example.com", "SenhaInicial123!").run(null);

        verify(entityManager, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(entityManager, never()).createQuery(anyString(), eq(Atendente.class));
    }

    @Test
    void rejeitaPrimeiraCredencialParaAtendenteSemPerfilAdministrativo() {
        Atendente atendente = new Atendente(7L, "Ana", "ana@example.com",
                new Empresa(1L, "WXN"), Atendente.PerfilAcesso.ATENDENTE);
        when(contagem.getSingleResult()).thenReturn(0L);
        when(buscaAtendente.getResultList()).thenReturn(List.of(atendente));

        var bootstrap = new CredencialAdministradorBootstrap(entityManager, encoder,
                "ana@example.com", "SenhaInicial123!");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(entityManager, never()).persist(org.mockito.ArgumentMatchers.any());
    }
}
