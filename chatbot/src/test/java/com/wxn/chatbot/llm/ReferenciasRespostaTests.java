package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReferenciasRespostaTests {
    private static final FonteDisponivel HORARIO = new FonteDisponivel(1L, 10L, "v2", "Política de atendimento",
            "t-3", "Seção 2 - Horários", "Atendemos de segunda a sexta, das 8h às 18h.");
    private static final FonteDisponivel PRAZO = new FonteDisponivel(1L, 11L, "v1", "Manual de entregas",
            "t-7", null, "O prazo de entrega é de 5 dias úteis.");
    private static final FonteDisponivel TROCA = new FonteDisponivel(1L, 12L, "v4", "Trocas e devoluções",
            "t-1", "Seção 1", "Trocas em até 30 dias.");

    @Test
    void renumeraCitacoesNaOrdemEmQueAparecem() {
        var resultado = ReferenciasResposta.extrair(
                "O prazo é de 5 dias úteis [F2]. Atendemos das 8h às 18h [F1].", List.of(HORARIO, PRAZO, TROCA));

        assertEquals("O prazo é de 5 dias úteis [1]. Atendemos das 8h às 18h [2].", resultado.texto());
        assertEquals(List.of(1, 2), resultado.citadas().stream().map(ReferenciasResposta.Citacao::numero).toList());
        assertEquals(List.of(PRAZO, HORARIO), resultado.citadas().stream().map(ReferenciasResposta.Citacao::fonte).toList());
        assertEquals(0, resultado.referenciasInvalidas());
    }

    @Test
    void aceitaGrupoRepetidoEMaiusculasMinusculas() {
        var resultado = ReferenciasResposta.extrair("Trocas em 30 dias [f3, F1]. Reforçando [F3].", List.of(HORARIO, PRAZO, TROCA));

        assertEquals("Trocas em 30 dias [1, 2]. Reforçando [1].", resultado.texto());
        assertEquals(List.of(TROCA, HORARIO), resultado.citadas().stream().map(ReferenciasResposta.Citacao::fonte).toList());
    }

    @Test
    void descartaReferenciasInventadas() {
        var resultado = ReferenciasResposta.extrair(
                "O prazo é de 5 dias [F9]. Atendemos das 8h [F1, F4]. Veja também [F0].", List.of(HORARIO, PRAZO));

        assertEquals("O prazo é de 5 dias. Atendemos das 8h [1]. Veja também.", resultado.texto());
        assertEquals(List.of(HORARIO), resultado.citadas().stream().map(ReferenciasResposta.Citacao::fonte).toList());
        assertEquals(3, resultado.referenciasInvalidas());
    }

    @Test
    void semFontesFornecidasNenhumaReferenciaEAceita() {
        var resultado = ReferenciasResposta.extrair("Não encontrei essa informação [F1].", List.of());

        assertEquals("Não encontrei essa informação.", resultado.texto());
        assertTrue(resultado.citadas().isEmpty());
        assertEquals(1, resultado.referenciasInvalidas());
    }

    @Test
    void textoSemMarcasFicaIgualEOutrosColchetesSaoPreservados() {
        var resultado = ReferenciasResposta.extrair("Use o comando [Menu] e depois\n\nescolha [2].", List.of(HORARIO));

        assertEquals("Use o comando [Menu] e depois\n\nescolha [2].", resultado.texto());
        assertTrue(resultado.citadas().isEmpty());
        assertEquals(0, resultado.referenciasInvalidas());
    }

    @Test
    void trechosRepetidosContamUmaVezNaNumeracao() {
        var numeradas = ReferenciasResposta.numerar(List.of(HORARIO, HORARIO, PRAZO));
        assertEquals(List.of(HORARIO, PRAZO), numeradas);
        assertEquals("F1", ReferenciasResposta.rotulo(0));
        assertEquals("F2", ReferenciasResposta.rotulo(1));

        var resultado = ReferenciasResposta.extrair("Prazo [F2].", List.of(HORARIO, HORARIO, PRAZO));
        assertEquals(PRAZO, resultado.citadas().getFirst().fonte());
    }

    @Test
    void rejeitaRespostaVaziaOuSoComReferencias() {
        assertThrows(IllegalArgumentException.class, () -> ReferenciasResposta.extrair(" ", List.of(HORARIO)));
        assertThrows(IllegalArgumentException.class, () -> ReferenciasResposta.extrair("[F7]", List.of(HORARIO)));
    }

    @Test
    void fonteExigeDadosDeIdentificacao() {
        assertThrows(IllegalArgumentException.class,
                () -> new FonteDisponivel(1L, 10L, " ", "Título", "t-1", null, "texto"));
        assertThrows(IllegalArgumentException.class,
                () -> new FonteDisponivel(1L, 0L, "v1", "Título", "t-1", null, "texto"));
        assertThrows(NullPointerException.class,
                () -> new FonteDisponivel(null, 10L, "v1", "Título", "t-1", null, "texto"));
        assertNull(new FonteDisponivel(1L, 10L, "v1", "Título", "t-1", "  ", "texto").localizacao());
    }
}
