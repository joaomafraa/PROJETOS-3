package com.wxn.chatbot.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Barreira de isolamento aplicada imediatamente antes e depois da chamada a IA.
 *
 * A busca pode produzir candidatos, mas somente o contexto devolvido por
 * {@link #preparar} deve ser enviado ao provedor. Antes de persistir a resposta,
 * {@link #revalidar} consulta novamente a fonte autoritativa para cobrir uma
 * alteracao ou desativacao concorrente.
 */
@Component
public class SegurancaContextoConhecimento {

    public ContextoConhecimentoSeguro preparar(Long empresaId, List<FonteDisponivel> candidatas,
            DisponibilidadeFonteConhecimento disponibilidade) {
        validarEmpresa(empresaId);
        Objects.requireNonNull(disponibilidade, "A consulta de disponibilidade e obrigatoria.");

        List<FonteDisponivel> fontes = validarPertencimento(empresaId, candidatas);
        List<FonteDisponivel> ativas = fontes.stream()
                .filter(fonte -> estaDisponivel(empresaId, fonte, disponibilidade))
                .toList();
        return new ContextoConhecimentoSeguro(empresaId, ativas);
    }

    /**
     * Valida o tenant e remove repeticoes sem consultar estado de documento.
     * Usado tambem como defesa final no registro historico de fontes.
     */
    public List<FonteDisponivel> validarPertencimento(Long empresaId, List<FonteDisponivel> fontes) {
        validarEmpresa(empresaId);
        if (fontes == null || fontes.isEmpty()) {
            return List.of();
        }

        Map<String, FonteDisponivel> unicas = new LinkedHashMap<>();
        for (FonteDisponivel fonte : fontes) {
            if (fonte == null) {
                throw new IllegalArgumentException("A lista de fontes nao pode conter item nulo.");
            }
            if (!empresaId.equals(fonte.empresaId())) {
                throw new SecurityException("Uma fonte recuperada pertence a outra empresa.");
            }
            unicas.putIfAbsent(fonte.chave(), fonte);
        }
        return List.copyOf(unicas.values());
    }

    /**
     * Deve ser executado dentro da transacao que salvaria resposta e fontes.
     * Qualquer mudanca faz a geracao inteira ser descartada.
     */
    public void revalidar(ContextoConhecimentoSeguro contexto,
            DisponibilidadeFonteConhecimento disponibilidade) {
        Objects.requireNonNull(contexto, "O contexto de conhecimento e obrigatorio.");
        Objects.requireNonNull(disponibilidade, "A consulta de disponibilidade e obrigatoria.");

        List<String> indisponiveis = new ArrayList<>();
        for (FonteDisponivel fonte : contexto.fontes()) {
            if (!estaDisponivel(contexto.empresaId(), fonte, disponibilidade)) {
                indisponiveis.add(fonte.chave());
            }
        }
        if (!indisponiveis.isEmpty()) {
            throw new ContextoConhecimentoObsoletoException(
                    "Uma fonte mudou ou foi desativada durante a geracao; resposta descartada.");
        }
    }

    private static boolean estaDisponivel(Long empresaId, FonteDisponivel fonte,
            DisponibilidadeFonteConhecimento disponibilidade) {
        return disponibilidade.estaDisponivel(empresaId, fonte.documentoId(),
                fonte.documentoVersao(), fonte.trechoId());
    }

    private static void validarEmpresa(Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            throw new IllegalArgumentException("O id da empresa deve ser positivo.");
        }
    }
}
