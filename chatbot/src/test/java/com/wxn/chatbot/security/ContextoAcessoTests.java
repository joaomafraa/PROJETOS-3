
package com.wxn.chatbot.security;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextoAcessoTests {

    private EntityManager entityManager;
    private TypedQuery<Atendente> query;
    private ContextoAcesso contexto;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void configurar() {
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);
        contexto = new ContextoAcesso(entityManager);

        when(entityManager.createQuery(
                anyString(), eq(Atendente.class)))
                .thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.setMaxResults(anyInt()))
                .thenReturn(query);
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String email) {
        var autenticacao =
                new UsernamePasswordAuthenticationToken(
                        email, null, List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(autenticacao);
    }

    @Test
    void deveBloquearUsuarioNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> contexto.atendenteAtual());
    }

    @Test
    void deveIdentificarEmpresaDoAtendente() {
        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente atendente = new Atendente(
                1L, "Gabriel", "gabriel@teste.com",
                empresa, Atendente.PerfilAcesso.ATENDENTE);

        autenticar("gabriel@teste.com");

        when(query.getResultList())
                .thenReturn(List.of(atendente));

        assertEquals(1L, contexto.empresaIdAtual());
    }

    @Test
    void deveImpedirAcessoOutraEmpresa() {
        Empresa empresaA = new Empresa(1L, "Empresa A");
        Empresa empresaB = new Empresa(2L, "Empresa B");

        Atendente atendente = new Atendente(
                1L, "Gabriel", "gabriel@teste.com",
                empresaA, Atendente.PerfilAcesso.ATENDENTE);

        autenticar("gabriel@teste.com");

        when(query.getResultList())
                .thenReturn(List.of(atendente));

        assertThrows(
                AccessDeniedException.class,
                () -> contexto.exigirMesmaEmpresa(empresaB));
    }

    @Test
    void deveBloquearOperacaoAdministrativa() {
        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente atendente = new Atendente(
                1L, "Gabriel", "gabriel@teste.com",
                empresa, Atendente.PerfilAcesso.ATENDENTE);

        autenticar("gabriel@teste.com");

        when(query.getResultList())
                .thenReturn(List.of(atendente));

        assertThrows(
                AccessDeniedException.class,
                () -> contexto.exigirAdministrador());
    }

    @Test
    void devePermitirOperacaoAdministrativa() {
        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente administrador = new Atendente(
                1L, "Administrador", "admin@teste.com",
                empresa, Atendente.PerfilAcesso.ADMINISTRADOR);

        autenticar("admin@teste.com");

        when(query.getResultList())
                .thenReturn(List.of(administrador));

        assertDoesNotThrow(
                () -> contexto.exigirAdministrador());
    }
    
    @Test
    void deveBloquearAtendenteInativoMesmoAutenticado() {

        Empresa empresa = new Empresa(1L, "Empresa A");

        Atendente administrador = new Atendente(
                2L,
                "Administrador",
                "admin@teste.com",
                empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR
        );

        Atendente atendente = new Atendente(
                1L,
                "Gabriel",
                "gabriel@teste.com",
                empresa,
                Atendente.PerfilAcesso.ATENDENTE
        );

    
        atendente.desativar(administrador);

        autenticar("gabriel@teste.com");

        when(query.getResultList())
                .thenReturn(List.of(atendente));

   
        assertThrows(
                AccessDeniedException.class,
                () -> contexto.atendenteAtual()
        );
    }

}
