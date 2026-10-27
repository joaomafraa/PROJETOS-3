package com.wxn.chatbot.web;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.service.ExemploAtendimentoService;

@Profile("painel-local")
@RestController
public class ExemploAtendimentoController {
    private final ExemploAtendimentoService exemplo;

    public ExemploAtendimentoController(ExemploAtendimentoService exemplo) {
        this.exemplo = exemplo;
    }

    @PostMapping(value = "/api/historico/exemplo", consumes = "application/json")
    public Map<String, Long> criar() {
        try {
            return Map.of("conversaId", exemplo.criar());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
