package com.wxn.chatbot.llm;

import java.util.ArrayList;
import java.util.List;


public final class DivisorTrechos {

    /** Tamanho maximo de cada trecho, em caracteres. */
    public static final int TAMANHO_MAXIMO = 800;

    public record Trecho(String id, String localizacao, String texto) { }

    private DivisorTrechos() { }

    public static List<Trecho> dividir(String conteudo) {
        List<Trecho> trechos = new ArrayList<>();
        if (conteudo == null || conteudo.isBlank()) return trechos;

        String secao = null;
        StringBuilder atual = new StringBuilder();
        for (String paragrafo : conteudo.replace("\r\n", "\n").replace('\r', '\n').split("\n\\s*\n")) {
            for (String bloco : separarTitulos(paragrafo)) {
                String titulo = titulo(bloco);
                if (titulo != null) {
                    fechar(trechos, secao, atual);
                    secao = titulo;
                    continue;
                }
                for (String parte : quebrar(bloco.strip())) {
                    if (parte.isEmpty()) continue;
                    if (atual.length() > 0 && atual.length() + 2 + parte.length() > TAMANHO_MAXIMO) {
                        fechar(trechos, secao, atual);
                    }
                    if (atual.length() > 0) atual.append("\n\n");
                    atual.append(parte);
                }
            }
        }
        fechar(trechos, secao, atual);
        return trechos;
    }

    // Um paragrafo pode comecar com o titulo na primeira linha ("# Prazos\nTexto...").
    private static List<String> separarTitulos(String paragrafo) {
        List<String> blocos = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        for (String linha : paragrafo.split("\n")) {
            if (titulo(linha) != null) {
                if (!texto.isEmpty()) blocos.add(texto.toString());
                texto.setLength(0);
                blocos.add(linha);
            } else {
                if (!texto.isEmpty()) texto.append('\n');
                texto.append(linha);
            }
        }
        if (!texto.isEmpty()) blocos.add(texto.toString());
        return blocos;
    }

    private static String titulo(String linha) {
        String limpa = linha.strip();
        if (!limpa.matches("#{1,6}\\s+\\S.*")) return null;
        String titulo = limpa.replaceFirst("^#{1,6}\\s+", "").replaceAll("\\s*#+$", "").strip();
        return titulo.isEmpty() ? null : (titulo.length() > 150 ? titulo.substring(0, 150) : titulo);
    }

    private static void fechar(List<Trecho> trechos, String secao, StringBuilder atual) {
        String texto = atual.toString().strip();
        atual.setLength(0);
        if (texto.isEmpty()) return;
        trechos.add(new Trecho("t" + (trechos.size() + 1), secao == null ? null : "Seção: " + secao, texto));
    }

    private static List<String> quebrar(String texto) {
        List<String> partes = new ArrayList<>();
        if (texto.length() <= TAMANHO_MAXIMO) {
            partes.add(texto);
            return partes;
        }
        StringBuilder atual = new StringBuilder();
        for (String frase : texto.split("(?<=[.!?;])\\s+")) {
            for (String pedaco : frase.length() > TAMANHO_MAXIMO ? porPalavras(frase) : List.of(frase)) {
                if (atual.length() > 0 && atual.length() + 1 + pedaco.length() > TAMANHO_MAXIMO) {
                    partes.add(atual.toString());
                    atual.setLength(0);
                }
                if (atual.length() > 0) atual.append(' ');
                atual.append(pedaco);
            }
        }
        if (atual.length() > 0) partes.add(atual.toString());
        return partes;
    }

    private static List<String> porPalavras(String frase) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : frase.split("\\s+")) {
            while (palavra.length() > TAMANHO_MAXIMO) { // palavra gigante (ex.: URL longa)
                if (atual.length() > 0) { partes.add(atual.toString()); atual.setLength(0); }
                partes.add(palavra.substring(0, TAMANHO_MAXIMO));
                palavra = palavra.substring(TAMANHO_MAXIMO);
            }
            if (atual.length() > 0 && atual.length() + 1 + palavra.length() > TAMANHO_MAXIMO) {
                partes.add(atual.toString());
                atual.setLength(0);
            }
            if (atual.length() > 0) atual.append(' ');
            atual.append(palavra);
        }
        if (atual.length() > 0) partes.add(atual.toString());
        return partes;
    }
}
