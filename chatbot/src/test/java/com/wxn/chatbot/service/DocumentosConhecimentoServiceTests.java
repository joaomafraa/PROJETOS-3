package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wxn.chatbot.model.DocumentoConhecimento;
import com.wxn.chatbot.repository.DocumentoConhecimentoRepository;
import com.wxn.chatbot.security.ContextoAcesso;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentosConhecimentoServiceTests {
    @Test void buscaPelaEmpresaDoAdministradorEBloqueiaDocumentoAlheio() {
        var repo = mock(DocumentoConhecimentoRepository.class); var acesso = mock(ContextoAcesso.class);
        when(acesso.empresaIdAtual()).thenReturn(1L);
        when(repo.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.empty());
        var service = new DocumentosConhecimentoService(repo, acesso);
        assertThrows(java.util.NoSuchElementException.class, () -> service.ativar(10L, false));
        verify(acesso).exigirAdministrador(); verify(repo).findByIdAndEmpresaId(10L, 1L);
    }
    @Test void buscaInternaAceitaSomenteAtivosDaEmpresa() {
        var repo = mock(DocumentoConhecimentoRepository.class); var acesso = mock(ContextoAcesso.class);
        when(repo.findByEmpresaIdAndAtivoTrueOrderByIdDesc(2L)).thenReturn(List.<DocumentoConhecimento>of());
        var service = new DocumentosConhecimentoService(repo, acesso);
        assertTrue(service.ativosDaEmpresa(2L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.ativosDaEmpresa(null));
        verify(repo).findByEmpresaIdAndAtivoTrueOrderByIdDesc(2L);
    }
}
