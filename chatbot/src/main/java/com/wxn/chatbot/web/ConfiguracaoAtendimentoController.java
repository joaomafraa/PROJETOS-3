package com.wxn.chatbot.web;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.ConfiguracaoAtendimentoResumo;
import com.wxn.chatbot.dto.SalvarConfiguracaoAtendimentoComando;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.ConfiguracaoAtendimentoService;

@RestController
@RequestMapping("/api/admin/configuracao-atendimento")
public class ConfiguracaoAtendimentoController {

    private final ConfiguracaoAtendimentoService configuracao;
    private final ContextoAcesso contexto;

    public ConfiguracaoAtendimentoController(ConfiguracaoAtendimentoService configuracao, ContextoAcesso contexto) {
        this.configuracao = configuracao;
        this.contexto = contexto;
    }

    @GetMapping
    public ConfiguracaoAtendimentoResumo obter() {
        return executar(() -> configuracao.obter(contexto.atendenteIdAtual()));
    }

    // Substitui a configuracao inteira (horarios, assuntos e mensagem).
    @PutMapping
    public ConfiguracaoAtendimentoResumo salvar(@RequestBody SalvarConfiguracaoAtendimentoComando dados) {
        return executar(() -> configuracao.salvar(contexto.atendenteIdAtual(), dados));
    }

    private <T> T executar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responderErro(ResponseStatusException erro) {
        String mensagem = erro.getReason() == null ? "Não foi possível salvar a configuração." : erro.getReason();
        return ResponseEntity.status(erro.getStatusCode()).body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> responderDadosInvalidos(HttpMessageNotReadableException erro) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Os dados enviados estão em um formato inválido."));
    }
}
