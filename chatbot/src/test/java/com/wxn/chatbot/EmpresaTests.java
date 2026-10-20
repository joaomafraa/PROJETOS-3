package com.wxn.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmpresaTests {

    @Test
    void criaEmpresaComDadosValidos() {
        Empresa empresa = new Empresa(1L, "WXN");

        assertEquals(1L, empresa.getId());
        assertEquals("WXN", empresa.getNome());
    }

    @Test
    void rejeitaIdNulo() {
        assertThrows(NullPointerException.class, () -> new Empresa(null, "WXN"));
    }

    @Test
    void rejeitaIdZeroOuNegativo() {
        assertThrows(IllegalArgumentException.class, () -> new Empresa(0L, "WXN"));

        assertThrows(IllegalArgumentException.class, () -> new Empresa(-1L, "WXN"));
    }

    @Test
    void rejeitaNomeNuloOuEmBranco() {
        assertThrows(NullPointerException.class, () -> new Empresa(1L, null));

        assertThrows(IllegalArgumentException.class, () -> new Empresa(1L, ""));

        assertThrows(IllegalArgumentException.class, () -> new Empresa(1L, "   "));
    }

    @Test
    void permiteAlterarNome() {
        Empresa empresa = new Empresa(1L, "WXN");

        empresa.alterarNome("WXN Tecnologia");

        assertEquals("WXN Tecnologia", empresa.getNome());
    }

    @Test
    void naoPermiteAlterarParaNomeInvalido() {
        Empresa empresa = new Empresa(1L, "WXN");

        assertThrows(IllegalArgumentException.class, () -> empresa.alterarNome(""));

        assertEquals("WXN", empresa.getNome());
    }
}
