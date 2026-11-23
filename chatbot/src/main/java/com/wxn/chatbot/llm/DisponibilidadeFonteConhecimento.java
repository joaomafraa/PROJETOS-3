package com.wxn.chatbot.llm;

/**
 * Consulta autoritativa usada na fronteira entre a busca documental e a IA.
 *
 * A implementacao deve consultar o documento/trecho usando todos os campos
 * recebidos e devolver {@code true} somente quando ele ainda pertencer a
 * empresa, estiver ativo e conservar a mesma versao. Resultados de cache, se
 * existirem, tambem precisam ser particionados por empresa e invalidados na
 * alteracao ou desativacao do documento.
 */
@FunctionalInterface
public interface DisponibilidadeFonteConhecimento {

    boolean estaDisponivel(Long empresaId, Long documentoId, String documentoVersao, String trechoId);
}
