package com.wxn.chatbot.llm;

/** A geracao deve ser descartada porque uma das fontes mudou ou foi desativada. */
public class ContextoConhecimentoObsoletoException extends IllegalStateException {

    public ContextoConhecimentoObsoletoException(String mensagem) {
        super(mensagem);
    }
}
