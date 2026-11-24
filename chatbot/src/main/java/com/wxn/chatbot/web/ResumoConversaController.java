package com.wxn.chatbot.web;

import com.wxn.chatbot.dto.ResumoEstruturadoConversa;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.ResumoEstruturadoConversaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ResumoConversaController {
    private final ResumoEstruturadoConversaService resumos;
    private final ContextoAcesso acesso;
    public ResumoConversaController(ResumoEstruturadoConversaService resumos, ContextoAcesso acesso) {
        this.resumos = resumos; this.acesso = acesso;
    }
    @GetMapping("/api/historico/{id}/resumo")
    public ResumoEstruturadoConversa consultar(@PathVariable Long id) {
        try { return resumos.gerar(id, acesso.atendenteAtual()); }
        catch (IllegalArgumentException erro) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada."); }
    }
}
