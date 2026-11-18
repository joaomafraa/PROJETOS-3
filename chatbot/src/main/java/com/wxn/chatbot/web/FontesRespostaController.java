package com.wxn.chatbot.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.FontesMensagem;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.FontesRespostaService;

// HU02.2: origem das respostas automaticas de uma conversa.
// A empresa vem do atendente autenticado; conversa de outra empresa responde 404.
//
// GET /api/historico/{conversaId}/fontes
@RestController
public class FontesRespostaController {
    private final FontesRespostaService fontes;
    private final ContextoAcesso contextoAcesso;

    public FontesRespostaController(FontesRespostaService fontes, ContextoAcesso contextoAcesso) {
        this.fontes = fontes;
        this.contextoAcesso = contextoAcesso;
    }

    @GetMapping("/api/historico/{conversaId}/fontes")
    public List<FontesMensagem> listar(@PathVariable Long conversaId) {
        var atendente = contextoAcesso.atendenteAtual();
        try {
            return fontes.listarPorConversa(conversaId, atendente);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada.");
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }
}
