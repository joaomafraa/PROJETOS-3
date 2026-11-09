
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AtendenteUserDetailsServiceTests {

    private EntityManager entityManager;
    private TypedQuery<Atendente> query;
    private AtendenteUserDetailsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void configurar() {
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);

        service = new AtendenteUserDetailsService(entityManager);

        when(entityManager.createQuery(
                anyString(), eq(Atendente.class)))
                .thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.setMaxResults(anyInt()))
                .thenReturn(query);
    }

    @Test
    void deveAutenticarAtendenteAtivo() {
        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente atendente = new Atendente(
                1L,
                "Gabriel",
                "gabriel@teste.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE
        );

        when(query.getResultList()).thenReturn(
                java.util.List.of(atendente));

        when(entityManager.find(
                CredencialAtendente.class, 1L))
                .thenReturn(new CredencialAtendente(
                        1L, "hash-de-teste"));

        var usuario = service.loadUserByUsername(
                "gabriel@teste.com");

        assertTrue(usuario.isEnabled());

        assertEquals(
                "gabriel@teste.com",
                usuario.getUsername());

        assertTrue(usuario.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority()
                        .equals("ROLE_ATENDENTE")));
    }

    @Test
    void deveBloquearAtendenteSemCredencial() {
        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente atendente = new Atendente(
                2L,
                "Ana",
                "ana@teste.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE
        );

        when(query.getResultList()).thenReturn(
                java.util.List.of(atendente));

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "ana@teste.com"));
    }

    @Test
    void deveRejeitarEmailDesconhecido() {
        when(query.getResultList()).thenReturn(
                java.util.List.of());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "desconhecido@teste.com"));
    }

    @Test
    void deveRejeitarEmailVazio() {
        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(" "));
    }

    
    @Test
    void deveMarcarAtendenteInativoComoDesabilitado() {

        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente administrador = new Atendente(
                1L,
                "Administrador",
                "admin@teste.com",
                empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR
        );

        Atendente atendente = new Atendente(
                3L,
                "Pedro",
                "pedro@teste.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE
        );

        
        atendente.desativar(administrador);

        when(query.getResultList()).thenReturn(
                java.util.List.of(atendente));

        when(entityManager.find(
                CredencialAtendente.class, 3L))
                .thenReturn(new CredencialAtendente(
                        3L, "hash-de-teste"));

            var usuario = service.loadUserByUsername(
            "pedro@teste.com");

    assertFalse(usuario.isEnabled());
} 

} 
