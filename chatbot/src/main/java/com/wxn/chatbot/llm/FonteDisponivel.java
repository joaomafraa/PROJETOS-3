package com.wxn.chatbot.llm;

import java.util.Objects;

/**
 * Trecho oficial entregue a IA para uma resposta (HU02.2).
 *
 * Contrato entre a busca nos documentos, a geracao e o registro de fontes:
 * a busca devolve os trechos da empresa, a geracao os numera para o prompt
 * (ver {@link ReferenciasResposta}) e o registro guarda quais foram usados.
 *
 * O titulo e a localizacao sao copiados no registro para que a origem de uma
 * resposta antiga continue identificavel mesmo depois de o documento ser
 * desativado ou ganhar nova versao.
 */
public record FonteDisponivel(Long empresaId, Long documentoId, String documentoVersao,
        String documentoTitulo, String trechoId, String localizacao, String texto) {

    public FonteDisponivel {
        Objects.requireNonNull(empresaId, "A empresa da fonte e obrigatoria.");
        Objects.requireNonNull(documentoId, "O documento da fonte e obrigatorio.");
        if (empresaId <= 0 || documentoId <= 0) {
            throw new IllegalArgumentException("Empresa e documento da fonte devem ser positivos.");
        }
        documentoVersao = obrigatorio(documentoVersao, "versao do documento");
        documentoTitulo = obrigatorio(documentoTitulo, "titulo do documento");
        trechoId = obrigatorio(trechoId, "identificador do trecho");
        texto = obrigatorio(texto, "texto do trecho");
        localizacao = localizacao == null || localizacao.isBlank() ? null : localizacao.strip();
    }

    /** Identifica o trecho dentro da versao do documento; usado para impedir duplicatas e referencias falsas. */
    public String chave() {
        return documentoId + "|" + documentoVersao + "|" + trechoId;
    }

    private static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " da fonte e obrigatorio.");
        }
        return valor.strip();
    }
}
