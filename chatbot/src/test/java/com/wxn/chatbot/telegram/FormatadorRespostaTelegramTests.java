package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wxn.chatbot.dto.FontesMensagem;

class FormatadorRespostaTelegramTests {
    @Test void removeMarcadoresDeNegritoSemAlterarOConteudo() {
        assertEquals("Horário: segunda a sexta [1].", FormatadorRespostaTelegram.formatar("**Horário**: segunda a sexta [1].", java.util.List.of()));
    }
    private static FontesMensagem.Fonte fonte(int numero, String titulo, String localizacao) {
        return new FontesMensagem.Fonte(numero, 10L + numero, "v1", titulo, "t-" + numero, localizacao, "trecho");
    }

    @Test
    void semFontesApenasOrganizaOTexto() {
        assertEquals("Olá!\n\nPosso ajudar?", FormatadorRespostaTelegram.formatar("  Olá!   \r\n\r\n\r\n\nPosso ajudar?  ", List.of()));
        assertEquals("Olá", FormatadorRespostaTelegram.formatar("Olá", null));
    }

    @Test
    void acrescentaFontesComTituloESecaoSemIdsInternos() {
        String texto = FormatadorRespostaTelegram.formatar("Atendemos das 8h às 18h [1].",
                List.of(fonte(1, "Política de atendimento", "Seção 2 - Horários")));

        assertEquals("Atendemos das 8h às 18h [1].\n\nFontes:\n[1] Política de atendimento — Seção 2 - Horários", texto);
        assertFalse(texto.contains("t-1"), "O id do trecho nao deve ir para o cliente.");
        assertFalse(texto.contains("11"), "O id do documento nao deve ir para o cliente.");
    }

    @Test
    void listaNoMaximoCincoFontesEInformaAsRestantes() {
        List<FontesMensagem.Fonte> citadas = new ArrayList<>();
        for (int numero = 1; numero <= 7; numero++) citadas.add(fonte(numero, "Documento " + numero, null));

        String texto = FormatadorRespostaTelegram.formatar("Resposta.", citadas);

        assertTrue(texto.contains("[5] Documento 5"));
        assertFalse(texto.contains("[6] Documento 6"));
        assertTrue(texto.endsWith("(e mais 2 fontes)"));
    }

    @Test
    void encurtaTitulosLongosEmUmaLinha() {
        String titulo = "Manual\ncompleto " + "x".repeat(200);
        String texto = FormatadorRespostaTelegram.formatar("Ok.", List.of(fonte(1, titulo, null)));
        String linha = texto.substring(texto.indexOf("[1] "));

        assertFalse(linha.contains("\n"));
        assertTrue(linha.length() <= 4 + 80);
        assertTrue(linha.endsWith("…"));
    }

    @Test
    void respeitaOLimiteDoTelegramSemCortarAsFontes() {
        String corpo = ("Frase longa sobre o serviço [1]. ").repeat(300);
        String texto = FormatadorRespostaTelegram.formatar(corpo, List.of(fonte(1, "Política", "Seção 1")));

        assertTrue(texto.length() <= FormatadorRespostaTelegram.LIMITE_TELEGRAM);
        assertTrue(texto.endsWith("\n\nFontes:\n[1] Política — Seção 1"));
        String parteCorpo = texto.substring(0, texto.indexOf("\n\nFontes:"));
        assertTrue(parteCorpo.endsWith("…"));
        assertEquals(parteCorpo.chars().filter(c -> c == '[').count(), parteCorpo.chars().filter(c -> c == ']').count(),
                "Uma referencia [n] nao pode ficar pela metade.");
    }

    @Test
    void naoPartirReferenciaComVariosNumerosNoCorte() {
        // O limite cai logo depois de "[1, ": a referencia inteira deve sair do texto.
        String corpo = "a".repeat(4090) + " [1, 2] fim";
        String texto = FormatadorRespostaTelegram.formatar(corpo, List.of());

        assertTrue(texto.length() <= FormatadorRespostaTelegram.LIMITE_TELEGRAM);
        assertFalse(texto.contains("[1,"));
        assertTrue(texto.endsWith("…"));
    }

    @Test
    void naoPartirEmojiNoCorte() {
        String corpo = "a".repeat(4094) + "😀😀😀";
        String texto = FormatadorRespostaTelegram.formatar(corpo, List.of());

        assertTrue(texto.length() <= FormatadorRespostaTelegram.LIMITE_TELEGRAM);
        char antesDasReticencias = texto.charAt(texto.length() - 2);
        assertFalse(Character.isHighSurrogate(antesDasReticencias));
    }

    @Test
    void rejeitaRespostaVazia() {
        assertThrows(IllegalArgumentException.class, () -> FormatadorRespostaTelegram.formatar("  ", List.of()));
    }
}
