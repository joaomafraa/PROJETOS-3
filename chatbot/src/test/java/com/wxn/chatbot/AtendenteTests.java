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
}