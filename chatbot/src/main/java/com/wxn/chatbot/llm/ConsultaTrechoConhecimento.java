package com.wxn.chatbot.llm;

/** Consulta autoritativa da busca documental, particionada por empresa e versão.
 * A implementação deve retornar false para trechos removidos ou não indexados. */
@FunctionalInterface
public interface ConsultaTrechoConhecimento {
    boolean existe(Long empresaId, Long documentoId, String documentoVersao, String trechoId);
}
