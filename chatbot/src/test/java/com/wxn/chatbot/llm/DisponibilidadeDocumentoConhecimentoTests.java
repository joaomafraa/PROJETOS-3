package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wxn.chatbot.repository.DocumentoConhecimentoRepository;
import org.junit.jupiter.api.Test;

class DisponibilidadeDocumentoConhecimentoTests {
    @Test void consultaPorEmpresaDocumentoVersaoEEstado() {
        var repo = mock(DocumentoConhecimentoRepository.class);
        var disponibilidade = new DisponibilidadeDocumentoConhecimento(repo);
        when(repo.existsByIdAndEmpresaIdAndVersaoAndAtivoTrue(2L, 1L, 3)).thenReturn(true);
        assertTrue(disponibilidade.estaDisponivel(1L, 2L, "3"));
        assertFalse(disponibilidade.estaDisponivel(9L, 2L, "3"));
        assertFalse(disponibilidade.estaDisponivel(1L, 2L, "2"));
        assertFalse(disponibilidade.estaDisponivel(1L, 2L, "03"));
        assertFalse(disponibilidade.estaDisponivel(1L, 2L, "inválida"));
        assertFalse(disponibilidade.estaDisponivel(null, 2L, "3"));
        verify(repo).existsByIdAndEmpresaIdAndVersaoAndAtivoTrue(2L, 1L, 3);
    }
}
