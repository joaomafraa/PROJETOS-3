package com.wxn.chatbot.dto;

import java.util.List;

import com.wxn.chatbot.model.FonteResposta;

// Fontes de uma resposta automatica, como aparecem no painel.
// citadas: referenciadas pela resposta, com o numero [n] usado no texto.
// recuperadas: entregues a IA como contexto; nao comprovam cada frase da resposta.
public record FontesMensagem(Long mensagemId, List<Fonte> citadas, List<Fonte> recuperadas) {

    public FontesMensagem {
        citadas = List.copyOf(citadas);
        recuperadas = List.copyOf(recuperadas);
    }

    public record Fonte(Integer numero, Long documentoId, String documentoVersao, String documentoTitulo,
            String trechoId, String localizacao, String excerto) {

        public static Fonte de(FonteResposta fonte) {
            return new Fonte(fonte.getNumero(), fonte.getDocumentoId(), fonte.getDocumentoVersao(),
                    fonte.getDocumentoTitulo(), fonte.getTrechoId(), fonte.getLocalizacao(), fonte.getExcerto());
        }
    }
}
