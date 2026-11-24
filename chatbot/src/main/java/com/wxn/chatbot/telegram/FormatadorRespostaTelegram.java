package com.wxn.chatbot.telegram;

import java.util.List;

import com.wxn.chatbot.dto.FontesMensagem;

/**
 * Prepara a resposta automatica para o Telegram (HU02.2).
 *
 * - organiza quebras de linha e espacos;
 * - omite as referencias usadas internamente e o rodape de fontes;
 * - respeita o limite de 4096 caracteres do Telegram.
 *
 * Texto simples (sem parse_mode), entao titulos de documentos nao viram formatacao.
 */
public final class FormatadorRespostaTelegram {
    public static final int LIMITE_TELEGRAM = 4096;
    private static final String RETICENCIAS = "…";

    private FormatadorRespostaTelegram() {
    }

    public static String formatar(String texto, List<FontesMensagem.Fonte> citadas) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("A resposta nao pode estar vazia.");
        }
        String corpo = normalizar(removerReferencias(texto, citadas));
        if (corpo.length() > LIMITE_TELEGRAM) {
            corpo = cortar(corpo, LIMITE_TELEGRAM - RETICENCIAS.length()) + RETICENCIAS;
        }
        return corpo;
    }

    static String normalizar(String texto) {
        return texto.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String removerReferencias(String texto, List<FontesMensagem.Fonte> citadas) {
        if (citadas == null || citadas.isEmpty()) return texto;
        var numeros = citadas.stream().map(FontesMensagem.Fonte::numero)
                .filter(java.util.Objects::nonNull).map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        var padrao = java.util.regex.Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");
        var encontrados = padrao.matcher(texto);
        var resultado = new StringBuilder();
        while (encontrados.find()) {
            boolean somenteCitacoes = java.util.Arrays.stream(encontrados.group(1).split(","))
                    .map(String::strip).allMatch(numeros::contains);
            encontrados.appendReplacement(resultado, somenteCitacoes ? "" : java.util.regex.Matcher.quoteReplacement(encontrados.group()));
        }
        encontrados.appendTail(resultado);
        return resultado.toString().replaceAll("[ \\t]+([.,;:!?])", "$1").replaceAll(" {2,}", " ");
    }

    // Corta em um espaco ou quebra de linha, sem deixar "[1, 2" pela metade nem partir um emoji.
    private static String cortar(String corpo, int maximo) {
        int limite = Math.max(0, maximo);
        if (limite > 0 && Character.isHighSurrogate(corpo.charAt(limite - 1))) limite--;
        String parte = corpo.substring(0, limite);
        int espaco = Math.max(parte.lastIndexOf(' '), parte.lastIndexOf('\n'));
        if (espaco > limite / 2) parte = parte.substring(0, espaco);
        int abre = parte.lastIndexOf('[');
        if (abre > parte.lastIndexOf(']')) parte = parte.substring(0, abre);
        return parte.stripTrailing();
    }

}
