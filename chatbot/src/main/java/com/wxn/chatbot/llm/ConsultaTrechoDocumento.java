package com.wxn.chatbot.llm;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.repository.DocumentoConhecimentoRepository;

@Component
public class ConsultaTrechoDocumento implements ConsultaTrechoConhecimento {

    private final DocumentoConhecimentoRepository documentos;

    public ConsultaTrechoDocumento(DocumentoConhecimentoRepository documentos) {
        this.documentos = documentos;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existe(Long empresaId, Long documentoId, String documentoVersao, String trechoId) {
        if (empresaId == null || empresaId <= 0 || documentoId == null || documentoId <= 0
                || documentoVersao == null || trechoId == null || trechoId.isBlank()) return false;
        int versao;
        try {
            versao = Integer.parseInt(documentoVersao);
        } catch (NumberFormatException erro) {
            return false;
        }
        return documentos.findByIdAndEmpresaId(documentoId, empresaId)
                .filter(documento -> documento.isAtivo() && documento.getVersao() == versao)
                .map(documento -> DivisorTrechos.dividir(documento.getConteudo()).stream()
                        .anyMatch(trecho -> trecho.id().equals(trechoId)))
                .orElse(false);
    }
}
