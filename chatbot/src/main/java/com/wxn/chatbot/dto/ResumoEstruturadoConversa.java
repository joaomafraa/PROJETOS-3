package com.wxn.chatbot.dto;

import java.time.Instant;
import java.util.List;

import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;

/** Resumo sem interpretacao automatica; campos ausentes sao null e constam em dadosAusentes. */
public record ResumoEstruturadoConversa(Long conversaId, Conversa.Status status, Instant dataInicio,
        ClienteDados cliente, TrechoMensagem trechoInicialCliente, List<TrechoMensagem> mensagensRecentes,
        List<String> dadosAusentes) {

    public ResumoEstruturadoConversa {
        mensagensRecentes = List.copyOf(mensagensRecentes);
        dadosAusentes = List.copyOf(dadosAusentes);
    }

    public record ClienteDados(Long id, String nome, String telefone, String email) { }

    /** O conteudo e uma citacao literal, que pode ter sido truncada. */
    public record TrechoMensagem(Long mensagemId, Mensagem.TipoRemetente remetente,
            Instant dataEnvio, String trecho, boolean truncado) { }
}
