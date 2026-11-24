package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class DivisorTrechosTests {

    @Test
    void tituloMarkdownAbreTrechoEViraLocalizacao() {
        List<DivisorTrechos.Trecho> trechos = DivisorTrechos.dividir("""
                Introdução sem título.

                # Prazos de entrega
                O prazo padrão é de 5 dias úteis.

                ## Trocas
                Trocas em até 7 dias.
                """);

        assertEquals(3, trechos.size());
        assertEquals(new DivisorTrechos.Trecho("t1", null, "Introdução sem título."), trechos.get(0));
        assertEquals(new DivisorTrechos.Trecho("t2", "Seção: Prazos de entrega", "O prazo padrão é de 5 dias úteis."), trechos.get(1));
        assertEquals(new DivisorTrechos.Trecho("t3", "Seção: Trocas", "Trocas em até 7 dias."), trechos.get(2));
    }

    @Test
    void tituloNaPrimeiraLinhaDoParagrafoTambemSepara() {
        List<DivisorTrechos.Trecho> trechos = DivisorTrechos.dividir("# Pagamento\nAceitamos Pix e boleto.");
        assertEquals(1, trechos.size());
        assertEquals("Seção: Pagamento", trechos.get(0).localizacao());
        assertEquals("Aceitamos Pix e boleto.", trechos.get(0).texto());
    }

    @Test
    void agrupaParagrafosCurtosERespeitaTamanhoMaximo() {
        String paragrafo = "Frase com conteúdo oficial da empresa. ".repeat(6).strip();
        String conteudo = String.join("\n\n", java.util.Collections.nCopies(12, paragrafo));

        List<DivisorTrechos.Trecho> trechos = DivisorTrechos.dividir(conteudo);

        assertTrue(trechos.size() > 1);
        trechos.forEach(t -> assertTrue(t.texto().length() <= DivisorTrechos.TAMANHO_MAXIMO, t.id()));
        assertTrue(trechos.get(0).texto().contains("\n\n"), "Paragrafos curtos devem ser agrupados.");
    }

    @Test
    void paragrafoGiganteEQuebradoSemPerderPalavras() {
        String longo = "palavra".repeat(3) + " " + "Uma frase comprida sobre a política de garantia. ".repeat(60)
                + "x".repeat(1900);

        List<DivisorTrechos.Trecho> trechos = DivisorTrechos.dividir(longo);

        trechos.forEach(t -> assertTrue(t.texto().length() <= DivisorTrechos.TAMANHO_MAXIMO));
        String juntado = String.join(" ", trechos.stream().map(DivisorTrechos.Trecho::texto).toList()).replaceAll("\\s+", "");
        assertEquals(longo.replaceAll("\\s+", ""), juntado);
    }

    @Test
    void mesmaEntradaGeraMesmosIdsEmSequencia() {
        String conteudo = "# A\nTexto A.\n\n# B\nTexto B.\n\n# C\nTexto C.";
        List<DivisorTrechos.Trecho> primeira = DivisorTrechos.dividir(conteudo);
        assertEquals(primeira, DivisorTrechos.dividir(conteudo));
        assertEquals(List.of("t1", "t2", "t3"), primeira.stream().map(DivisorTrechos.Trecho::id).toList());
    }

    @Test
    void conteudoVazioOuSoTitulosNaoGeraTrechos() {
        assertTrue(DivisorTrechos.dividir(null).isEmpty());
        assertTrue(DivisorTrechos.dividir("   \n\n ").isEmpty());
        assertTrue(DivisorTrechos.dividir("# Só título\n\n## Outro").isEmpty());
    }

    @Test
    void aceitaQuebraDeLinhaDoWindows() {
        List<DivisorTrechos.Trecho> trechos = DivisorTrechos.dividir("# Horário\r\nDas 8h às 18h.\r\n\r\nSábado fechado.");
        assertEquals(1, trechos.size());
        assertEquals("Das 8h às 18h.\n\nSábado fechado.", trechos.get(0).texto());
    }
}
