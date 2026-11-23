package com.wxn.chatbot.llm;

import com.wxn.chatbot.repository.DocumentoConhecimentoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consulta documental para compor a validação de trechos da busca.
 * Não verifica a existência de um trecho e não substitui DisponibilidadeFonteConhecimento. */
@Component
public class DisponibilidadeDocumentoConhecimento {
    private final DocumentoConhecimentoRepository documentos;

    public DisponibilidadeDocumentoConhecimento(DocumentoConhecimentoRepository documentos) {
        this.documentos = documentos;
    }

    @Transactional(readOnly = true)
    public boolean estaDisponivel(Long empresaId, Long documentoId, String documentoVersao) {
        if (empresaId == null || empresaId <= 0 || documentoId == null || documentoId <= 0 || documentoVersao == null) return false;
        try {
            int versao = Integer.parseInt(documentoVersao);
            if (versao <= 0 || !Integer.toString(versao).equals(documentoVersao)) return false;
            return documentos.existsByIdAndEmpresaIdAndVersaoAndAtivoTrue(documentoId, empresaId, versao);
        } catch (NumberFormatException erro) {
            return false;
        }
    }
}
