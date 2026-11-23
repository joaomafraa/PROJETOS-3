package com.wxn.chatbot.service;

import com.wxn.chatbot.model.DocumentoConhecimento;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.repository.DocumentoConhecimentoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentosConhecimentoService {
    private final DocumentoConhecimentoRepository documentos;
    private final ContextoAcesso acesso;
    public DocumentosConhecimentoService(DocumentoConhecimentoRepository documentos, ContextoAcesso acesso) {
        this.documentos = documentos; this.acesso = acesso;
    }
    public record Documento(Long id, String titulo, String conteudo, int versao, boolean ativo) {
        static Documento de(DocumentoConhecimento d) { return new Documento(d.getId(), d.getTitulo(), d.getConteudo(), d.getVersao(), d.isAtivo()); }
    }
    @Transactional(readOnly = true)
    public List<Documento> listar() {
        acesso.exigirAdministrador();
        return documentos.findByEmpresaIdOrderByIdDesc(acesso.empresaIdAtual()).stream().map(Documento::de).toList();
    }
    @Transactional
    public Documento criar(String titulo, String conteudo) {
        acesso.exigirAdministrador();
        return Documento.de(documentos.save(new DocumentoConhecimento(acesso.atendenteAtual().getEmpresa(), titulo, conteudo)));
    }
    @Transactional
    public Documento atualizar(Long id, String titulo, String conteudo) {
        DocumentoConhecimento d = obter(id); d.atualizar(titulo, conteudo); return Documento.de(d);
    }
    @Transactional
    public Documento ativar(Long id, boolean ativo) {
        DocumentoConhecimento d = obter(id); d.definirAtivo(ativo); return Documento.de(d);
    }
    private DocumentoConhecimento obter(Long id) {
        acesso.exigirAdministrador();
        return documentos.findByIdAndEmpresaId(id, acesso.empresaIdAtual()).orElseThrow(() -> new java.util.NoSuchElementException("Documento não encontrado."));
    }
    /** Somente para uso interno após validação da empresa no canal. */
    @Transactional(readOnly = true)
    public List<Documento> ativosDaEmpresa(Long empresaId) {
        if (empresaId == null || empresaId <= 0) throw new IllegalArgumentException("Empresa inválida.");
        return documentos.findByEmpresaIdAndAtivoTrueOrderByIdDesc(empresaId).stream().map(Documento::de).toList();
    }
}
