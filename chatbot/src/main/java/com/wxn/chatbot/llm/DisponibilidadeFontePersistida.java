package com.wxn.chatbot.llm;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Combina a situação do documento no banco com a verificação do índice de trechos. */
@Component
public class DisponibilidadeFontePersistida implements DisponibilidadeFonteConhecimento {
    private final DisponibilidadeDocumentoConhecimento documentos;
    private final ObjectProvider<ConsultaTrechoConhecimento> consultas;

    public DisponibilidadeFontePersistida(DisponibilidadeDocumentoConhecimento documentos,
            ObjectProvider<ConsultaTrechoConhecimento> consultas) {
        this.documentos = documentos;
        this.consultas = consultas;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaDisponivel(Long empresaId, Long documentoId, String documentoVersao, String trechoId) {
        if (trechoId == null || trechoId.isBlank()) return false;
        if (!documentos.estaDisponivel(empresaId, documentoId, documentoVersao)) return false;
        ConsultaTrechoConhecimento consulta = consultas.getIfAvailable();
        return consulta != null && consulta.existe(empresaId, documentoId, documentoVersao, trechoId);
    }
}
