package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DisponibilidadeFontePersistidaTests {
    @SuppressWarnings("unchecked")
    @Test void soLiberaTrechoConfirmadoDeDocumentoDisponivel() {
        var documentos = mock(DisponibilidadeDocumentoConhecimento.class);
        var consultas = (ObjectProvider<ConsultaTrechoConhecimento>) mock(ObjectProvider.class);
        var indice = mock(ConsultaTrechoConhecimento.class);
        var fonte = new DisponibilidadeFontePersistida(documentos, consultas);
        when(documentos.estaDisponivel(1L, 2L, "3")).thenReturn(true);
        assertFalse(fonte.estaDisponivel(1L, 2L, "3", "t1")); // sem índice, falha fechada
        when(consultas.getIfAvailable()).thenReturn(indice);
        assertFalse(fonte.estaDisponivel(1L, 2L, "3", "t1"));
        when(indice.existe(1L, 2L, "3", "t1")).thenReturn(true);
        assertTrue(fonte.estaDisponivel(1L, 2L, "3", "t1"));
        assertFalse(fonte.estaDisponivel(4L, 2L, "3", "t1"));
        assertFalse(fonte.estaDisponivel(1L, 2L, "3", " "));
        verify(indice, never()).existe(4L, 2L, "3", "t1");
    }
}
