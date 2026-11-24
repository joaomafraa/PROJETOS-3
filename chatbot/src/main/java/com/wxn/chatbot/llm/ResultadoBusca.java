package com.wxn.chatbot.llm;

import java.util.List;
import java.util.Objects;

public record ResultadoBusca(Situacao situacao, List<TrechoEncontrado> trechos) {

    public enum Situacao {
        ENCONTRADO,
        PERGUNTA_SEM_TERMOS,
        SEM_DOCUMENTOS,
        SEM_CORRESPONDENCIA
    }

    public record TrechoEncontrado(FonteDisponivel fonte, double relevancia) {
        public TrechoEncontrado {
            Objects.requireNonNull(fonte, "A fonte do trecho e obrigatoria.");
        }
    }

    public ResultadoBusca {
        Objects.requireNonNull(situacao, "A situacao da busca e obrigatoria.");
        trechos = trechos == null ? List.of() : List.copyOf(trechos);
        if ((situacao == Situacao.ENCONTRADO) == trechos.isEmpty()) {
            throw new IllegalArgumentException("Somente o resultado ENCONTRADO pode ter trechos, e precisa ter ao menos um.");
        }
    }

    static ResultadoBusca vazio(Situacao situacao) {
        return new ResultadoBusca(situacao, List.of());
    }

    public boolean encontrou() {
        return situacao == Situacao.ENCONTRADO;
    }

    /** Trechos no formato usado por SegurancaContextoConhecimento, ReferenciasResposta e FontesRespostaService. */
    public List<FonteDisponivel> fontes() {
        return trechos.stream().map(TrechoEncontrado::fonte).toList();
    }
}
