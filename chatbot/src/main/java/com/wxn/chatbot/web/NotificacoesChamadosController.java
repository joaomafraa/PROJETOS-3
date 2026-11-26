package com.wxn.chatbot.web;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.NotificacaoChamadoResumo;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.NotificacaoChamadosService;

// HU05.3: avisos de novos chamados do atendente logado.
// O destinatario e sempre quem esta logado; nenhum id de atendente ou empresa vem da tela.
@RestController
@RequestMapping("/api/notificacoes-chamados")
public class NotificacoesChamadosController {

    private final NotificacaoChamadosService notificacoes;
    private final ContextoAcesso contexto;

    public NotificacoesChamadosController(NotificacaoChamadosService notificacoes, ContextoAcesso contexto) {
        this.notificacoes = notificacoes;
        this.contexto = contexto;
    }

    @GetMapping
    public List<NotificacaoChamadoResumo> listar(@RequestParam(defaultValue = "false") boolean somenteNaoLidas) {
        return executar(() -> notificacoes.listar(contexto.atendenteIdAtual(), somenteNaoLidas));
    }

    @GetMapping("/contagem")
    public Map<String, Long> contagem() {
        return executar(() -> Map.of("naoLidas", notificacoes.contarNaoLidas(contexto.atendenteIdAtual())));
    }

    @PostMapping("/{id}/lida")
    public NotificacaoChamadoResumo marcarComoLida(@PathVariable Long id) {
        return executar(() -> notificacoes.marcarComoLida(contexto.atendenteIdAtual(), id));
    }

    @PostMapping("/lidas")
    public Map<String, Integer> marcarTodasComoLidas() {
        return executar(() -> Map.of("marcadas", notificacoes.marcarTodasComoLidas(contexto.atendenteIdAtual())));
    }

    private <T> T executar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificação não encontrada.");
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responderErro(ResponseStatusException erro) {
        String mensagem = erro.getReason() == null ? "Não foi possível concluir a operação." : erro.getReason();
        return ResponseEntity.status(erro.getStatusCode()).body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> responderIdInvalido(MethodArgumentTypeMismatchException erro) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Notificação inválida."));
    }
}
