package com.wxn.chatbot.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DocumentoConhecimentoTests {
    @Test void validaEVersionaApenasMudancas() {
        var d = new DocumentoConhecimento(new Empresa(1L, "Empresa"), " Manual ", " Texto ");
        assertEquals(1, d.getVersao());
        d.atualizar("Manual", "Texto"); assertEquals(1, d.getVersao());
        d.atualizar("Manual", "Novo texto"); assertEquals(2, d.getVersao());
        d.definirAtivo(false); assertFalse(d.isAtivo());
        assertThrows(IllegalArgumentException.class, () -> d.atualizar("", "Texto"));
        assertThrows(IllegalArgumentException.class, () -> d.atualizar("Manual", " "));
        assertThrows(IllegalArgumentException.class, () -> d.atualizar("Manual", "a".repeat(200001)));
        assertEquals("Novo texto", d.getConteudo());
    }
}
