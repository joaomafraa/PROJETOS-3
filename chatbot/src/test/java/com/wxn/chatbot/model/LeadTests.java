package com.wxn.chatbot.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LeadTests {

    private final Empresa empresa = new Empresa(1L, "WXN");

    @Test
    void criaLeadComTelefoneNormalizado() {
        Lead lead = new Lead(empresa, "  Maria  ", "+55 (81) 99999-8888", Lead.Origem.SITE,
                "  Quero um orçamento  ");

        assertEquals("Maria", lead.getNome());
        assertEquals("+5581999998888", lead.getContato());
        assertEquals(Lead.TipoContato.TELEFONE, lead.getTipoContato());
        assertEquals("Quero um orçamento", lead.getNecessidade());
        assertEquals(Lead.Origem.SITE, lead.getOrigem());
        assertNotNull(lead.getDataCriacao());
    }

    @Test
    void criaLeadComEmailNormalizadoENomeOpcional() {
        Lead lead = new Lead(empresa, " ", " Maria@Example.COM ", Lead.Origem.WHATSAPP, "Demonstração");

        assertNull(lead.getNome());
        assertEquals("maria@example.com", lead.getContato());
        assertEquals(Lead.TipoContato.EMAIL, lead.getTipoContato());
    }

    @Test
    void rejeitaCamposObrigatoriosInvalidos() {
        assertThrows(NullPointerException.class,
                () -> new Lead(null, null, "maria@example.com", Lead.Origem.SITE, "x"));
        assertThrows(NullPointerException.class,
                () -> new Lead(empresa, null, "maria@example.com", null, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, null, Lead.Origem.SITE, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "  ", Lead.Origem.SITE, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "maria@example.com", Lead.Origem.SITE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "maria@example.com", Lead.Origem.SITE, "   "));
    }

    @Test
    void rejeitaContatosEFormatosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "maria@", Lead.Origem.SITE, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "abc123", Lead.Origem.SITE, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "123", Lead.Origem.SITE, "x"));
    }

    @Test
    void rejeitaNecessidadeEOnomeMuitoLongos() {
        String longa = "a".repeat(Lead.TAMANHO_MAXIMO_NECESSIDADE + 1);
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, null, "maria@example.com", Lead.Origem.SITE, longa));
        assertThrows(IllegalArgumentException.class,
                () -> new Lead(empresa, "n".repeat(256), "maria@example.com", Lead.Origem.SITE, "x"));
    }

    @Test
    void chaveDaNecessidadeIgnoraCaixaEEspacosRepetidos() {
        assertEquals(Lead.chaveDaNecessidade("Quero  um\n Orçamento"),
                Lead.chaveDaNecessidade("quero um orçamento "));
    }
}
