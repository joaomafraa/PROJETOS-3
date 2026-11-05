package com.wxn.chatbot.dto;

import java.util.List;

// Pagina de resultados com os dados de navegacao para o frontend.
// A primeira pagina e a 0.
public record PaginaResumo<T>(List<T> conteudo, int pagina, int tamanho,
        long totalElementos, int totalPaginas) {

    public PaginaResumo {
        conteudo = List.copyOf(conteudo);
    }
}
