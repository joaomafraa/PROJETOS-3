package com.wxn.chatbot.telegram;

import java.util.List;

import com.wxn.chatbot.dto.FontesMensagem;

/**
 * Prepara a resposta automatica para o Telegram (HU02.2).
 *
 * - organiza quebras de linha e espacos;
 * - acrescenta a lista "Fontes" com titulo e secao dos documentos citados,
 *   sem ids internos, arquivos ou links;
 * - respeita o limite de 4096 caracteres do Telegram cortando o corpo, nunca a
 *   lista de fontes, e sem partir uma referencia [n] no meio.
 *
 * Texto simples (sem parse_mode), entao titulos de documentos nao viram formatacao.
 */
public final class FormatadorRespostaTelegram {
    public static final int LIMITE_TELEGRAM = 4096;
    static final int MAXIMO_FONTES_LISTADAS = 5;
    private static final int TAMANHO_TITULO = 80;
    private static final int TAMANHO_LOCALIZACAO = 60;
    private static final String RETICENCIAS = "…";

    private FormatadorRespostaTelegram() {
    }

    public static String formatar(String texto, List<FontesMensagem.Fonte> citadas) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("A resposta nao pode estar vazia.");
        }
        String corpo = normalizar(texto);
        String rodape = rodape(citadas == null ? List.of() : citadas);
        int espaco = LIMITE_TELEGRAM - rodape.length();
        if (corpo.length() > espaco) {
            corpo = cortar(corpo, espaco - RETICENCIAS.length()) + RETICENCIAS;
        }
        return corpo + rodape;
    }

    static String normalizar(String texto) {
        return texto.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String rodape(List<FontesMensagem.Fonte> citadas) {
        if (citadas.isEmpty()) return "";
        StringBuilder rodape = new StringBuilder("\n\nFontes:");
        citadas.stream().limit(MAXIMO_FONTES_LISTADAS).forEach(fonte -> {
            rodape.append('\n');
            if (fonte.numero() != null) rodape.append('[').append(fonte.numero()).append("] ");
            rodape.append(encurtar(umaLinha(fonte.documentoTitulo()), TAMANHO_TITULO));
            if (fonte.localizacao() != null && !fonte.localizacao().isBlank()) {
                rodape.append(" — ").append(encurtar(umaLinha(fonte.localizacao()), TAMANHO_LOCALIZACAO));
            }
        });
        int restantes = citadas.size() - MAXIMO_FONTES_LISTADAS;
        if (restantes > 0) {
            rodape.append("\n(e mais ").append(restantes).append(restantes == 1 ? " fonte)" : " fontes)");
        }
        return rodape.toString();
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

    private static String umaLinha(String valor) {
        return valor == null ? "" : valor.replaceAll("\\s+", " ").strip();
    }

    private static String encurtar(String valor, int maximo) {
        return valor.length() <= maximo ? valor : valor.substring(0, maximo - 1).stripTrailing() + RETICENCIAS;
    }
}
