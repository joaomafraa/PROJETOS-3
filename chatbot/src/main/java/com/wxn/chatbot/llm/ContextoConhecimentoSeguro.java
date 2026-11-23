package com.wxn.chatbot.llm;

import java.util.List;

/**
 * Trechos ja conferidos para uma empresa antes de montar o prompt.
 *
 * A lista e imutavel para impedir que uma fonte seja acrescentada entre a
 * validacao e a chamada ao provedor.
 */
public record ContextoConhecimentoSeguro(Long empresaId, List<FonteDisponivel> fontes) {

    public ContextoConhecimentoSeguro {
        if (empresaId == null || empresaId <= 0) {
            throw new IllegalArgumentException("O id da empresa deve ser positivo.");
        }
        fontes = List.copyOf(fontes);
        if (fontes.stream().anyMatch(fonte -> fonte == null || !empresaId.equals(fonte.empresaId()))) {
            throw new SecurityException("O contexto contem fonte de outra empresa.");
        }
    }
}
