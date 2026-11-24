package com.wxn.chatbot.telegram;

import static org.junit.jupiter.api.Assertions.*;

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
    void ocultaFontesEReferenciasNoTelegram() {
        String texto = FormatadorRespostaTelegram.formatar("Atendemos das 8h às 18h [1].",
                List.of(fonte(1, "Política de atendimento", "Seção 2 - Horários")));

        assertEquals("Atendemos das 8h às 18h.", texto);
        assertFalse(texto.contains("t-1"), "O id do trecho nao deve ir para o cliente.");
        assertFalse(texto.contains("11"), "O id do documento nao deve ir para o cliente.");
    }

    @Test
    void preservaNumerosQueNaoSaoReferenciasConhecidas() {
        String texto = FormatadorRespostaTelegram.formatar("Resposta [1, 2]. Código [42].", List.of(fonte(1, "A", null), fonte(2, "B", null)));
        assertEquals("Resposta. Código [42].", texto);
    }

    @Test
    void naoMostraTituloDoDocumento() {
        String texto = FormatadorRespostaTelegram.formatar("Ok [1].", List.of(fonte(1, "Manual", null)));
        assertEquals("Ok.", texto);
    }

    @Test
    void respeitaOLimiteDoTelegramSemMostrarFontes() {
        String corpo = ("Frase longa sobre o serviço [1]. ").repeat(300);
        String texto = FormatadorRespostaTelegram.formatar(corpo, List.of(fonte(1, "Política", "Seção 1")));

        assertTrue(texto.length() <= FormatadorRespostaTelegram.LIMITE_TELEGRAM);
        assertFalse(texto.contains("Fontes:"));
        assertFalse(texto.contains("[1]"));
        assertTrue(texto.endsWith("…"));
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
