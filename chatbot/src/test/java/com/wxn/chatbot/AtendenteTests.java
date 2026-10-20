package com.wxn.chatbot;
 
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
 
import org.junit.jupiter.api.Test;

class AtendenteTests {
 
    private final Empresa wxn = new Empresa(1L, "WXN");
    private final Empresa outraEmpresa = new Empresa(2L, "Outra");
    private final Atendente admin = new Atendente(1L, "Ana", "ana@wxn.com", wxn, Atendente.PerfilAcesso.ADMINISTRADOR);
 
    @Test
    void criaAtendenteAtivoComDadosValidos() {
        Atendente atendente = novoAtendente();
 
        assertAll(
            () -> assertEquals(10L, atendente.getId()),
            () -> assertEquals("Bruno", atendente.getNome()),
            () -> assertEquals("bruno@wxn.com", atendente.getEmail()),
            () -> assertSame(wxn, atendente.getEmpresa()),
            () -> assertEquals(Atendente.PerfilAcesso.ATENDENTE, atendente.getPerfil()),
            () -> assertTrue(atendente.isAtivo()));
    }

    @Test
    void normalizaNomeEEmail() {
        Atendente atendente = new Atendente(10L, "  Bruno  ", " Bruno@WXN.com ", wxn, Atendente.PerfilAcesso.ATENDENTE);
 
        assertEquals("Bruno", atendente.getNome());
        assertEquals("bruno@wxn.com", atendente.getEmail());
    }

    @Test
    void rejeitaDadosInvalidosNaCriacao() {
        var perfil = Atendente.PerfilAcesso.ATENDENTE;
        assertAll(
            () -> assertThrows(NullPointerException.class, () -> new Atendente(null, "Bruno", "b@wxn.com", wxn, perfil)),
            () -> assertThrows(IllegalArgumentException.class, () -> new Atendente(0L, "Bruno", "b@wxn.com", wxn, perfil)),
            () -> assertThrows(IllegalArgumentException.class, () -> new Atendente(-1L, "Bruno", "b@wxn.com", wxn, perfil)),
            () -> assertThrows(NullPointerException.class, () -> new Atendente(10L, null, "b@wxn.com", wxn, perfil)),
            () -> assertThrows(IllegalArgumentException.class, () -> new Atendente(10L, "  ", "b@wxn.com", wxn, perfil)),
            () -> assertThrows(NullPointerException.class, () -> new Atendente(10L, "Bruno", null, wxn, perfil)),
            () -> assertThrows(IllegalArgumentException.class, () -> new Atendente(10L, "Bruno", "bruno", wxn, perfil)),
            () -> assertThrows(IllegalArgumentException.class, () -> new Atendente(10L, "Bruno", "b @wxn.com", wxn, perfil)),
            () -> assertThrows(NullPointerException.class, () -> new Atendente(10L, "Bruno", "b@wxn.com", null, perfil)),
            () -> assertThrows(NullPointerException.class, () -> new Atendente(10L, "Bruno", "b@wxn.com", wxn, null)));
    }

    @Test
    void alteraNomeEEmailComValidacao() {
        Atendente atendente = novoAtendente();
 
        atendente.alterarNome("Bruno Lima");
        atendente.alterarEmail("bruno.lima@wxn.com");
        assertThrows(IllegalArgumentException.class, () -> atendente.alterarNome(""));
        assertThrows(IllegalArgumentException.class, () -> atendente.alterarEmail("invalido"));
        assertEquals("Bruno Lima", atendente.getNome());
        assertEquals("bruno.lima@wxn.com", atendente.getEmail());
    }
}