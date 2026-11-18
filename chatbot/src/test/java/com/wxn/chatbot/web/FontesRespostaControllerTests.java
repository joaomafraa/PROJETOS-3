package com.wxn.chatbot.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wxn.chatbot.dto.FontesMensagem;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.FontesRespostaService;

@ExtendWith(MockitoExtension.class)
class FontesRespostaControllerTests {
    @Mock private FontesRespostaService fontes;
    @Mock private ContextoAcesso contextoAcesso;
    private Atendente atendente;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        atendente = new Atendente(10L, "Ana", "ana@example.com", new Empresa(1L, "NEXUS"), Atendente.PerfilAcesso.ATENDENTE);
        lenient().when(contextoAcesso.atendenteAtual()).thenReturn(atendente);
        mvc = MockMvcBuilders.standaloneSetup(new FontesRespostaController(fontes, contextoAcesso))
                .setControllerAdvice(new AcessoNegado()).build();
    }

    @Test
    void devolveFontesDaConversaUsandoOAtendenteAutenticado() throws Exception {
        var citada = new FontesMensagem.Fonte(1, 10L, "v2", "Política", "t-3", "Seção 2", "Atendemos das 8h às 18h.");
        var consultada = new FontesMensagem.Fonte(null, 11L, "v1", "Manual", "t-7", null, "Prazo de 5 dias.");
        when(fontes.listarPorConversa(5L, atendente)).thenReturn(List.of(new FontesMensagem(4L, List.of(citada), List.of(citada, consultada))));

        mvc.perform(get("/api/historico/5/fontes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensagemId").value(4))
                .andExpect(jsonPath("$[0].citadas[0].numero").value(1))
                .andExpect(jsonPath("$[0].citadas[0].documentoTitulo").value("Política"))
                .andExpect(jsonPath("$[0].recuperadas.length()").value(2))
                .andExpect(jsonPath("$[0].recuperadas[1].numero").doesNotExist());

        verify(fontes).listarPorConversa(5L, atendente);
    }

    @Test
    void conversaDeOutraEmpresaOuInexistenteResponde404() throws Exception {
        when(fontes.listarPorConversa(9L, atendente)).thenThrow(new IllegalArgumentException("Conversa nao encontrada nesta empresa."));

        mvc.perform(get("/api/historico/9/fontes")).andExpect(status().isNotFound());
    }

    @Test
    void atendenteInativoOuSemLoginResponde403() throws Exception {
        when(fontes.listarPorConversa(5L, atendente)).thenThrow(new SecurityException("Um atendente inativo nao pode consultar fontes."));
        mvc.perform(get("/api/historico/5/fontes")).andExpect(status().isForbidden());

        when(contextoAcesso.atendenteAtual()).thenThrow(new AccessDeniedException("Atendente nao autenticado."));
        mvc.perform(get("/api/historico/6/fontes")).andExpect(status().isForbidden());
        verify(fontes, never()).listarPorConversa(eq(6L), any());
    }

    @RestControllerAdvice
    static class AcessoNegado {
        @ExceptionHandler(AccessDeniedException.class)
        org.springframework.http.ResponseEntity<Void> negar(AccessDeniedException erro) {
            return org.springframework.http.ResponseEntity.status(403).build();
        }
    }
}
