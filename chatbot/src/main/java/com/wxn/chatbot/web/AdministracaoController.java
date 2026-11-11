package com.wxn.chatbot.web;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.AtendenteResumo;
import com.wxn.chatbot.dto.CadastrarAtendenteComando;
import com.wxn.chatbot.dto.ResponsavelNotificacaoResumo;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.security.CredencialAtendenteService;
import com.wxn.chatbot.service.AdministracaoAtendentesService;
import com.wxn.chatbot.service.AtendenteDuplicadoException;
import com.wxn.chatbot.service.ResponsaveisNotificacaoService;

@RestController
@RequestMapping("/api/admin")
public class AdministracaoController {

    private final AdministracaoAtendentesService administracao;
    private final ResponsaveisNotificacaoService responsaveis;
    private final CredencialAtendenteService credenciais;
    private final ContextoAcesso contexto;

    public AdministracaoController(AdministracaoAtendentesService administracao,
            ResponsaveisNotificacaoService responsaveis,
            CredencialAtendenteService credenciais,
            ContextoAcesso contexto) {
        this.administracao = administracao;
        this.responsaveis = responsaveis;
        this.credenciais = credenciais;
        this.contexto = contexto;
    }

    public record NovoPerfil(PerfilAcesso perfil) {
    }

    public record NovaSenha(String senha) {
    }

    public record ListaResponsaveis(List<Long> atendenteIds) {
    }

    @GetMapping("/atendentes")
    public List<AtendenteResumo> listarAtendentes(@RequestParam(required = false) Boolean ativo) {
        return executar(() -> administracao.listar(contexto.atendenteIdAtual(), ativo));
    }

    @GetMapping("/atendentes/{id}")
    public AtendenteResumo buscarAtendente(@PathVariable Long id) {
        return executar(() -> administracao.buscar(contexto.atendenteIdAtual(), id));
    }

    @PostMapping("/atendentes")
    @ResponseStatus(HttpStatus.CREATED)
    public AtendenteResumo cadastrarAtendente(@RequestBody CadastrarAtendenteComando dados) {
        if (dados == null || dados.perfil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha o perfil de acesso do atendente.");
        }
        return executar(() -> administracao.cadastrar(contexto.atendenteIdAtual(), dados));
    }

    @PatchMapping("/atendentes/{id}/perfil")
    public AtendenteResumo alterarPerfil(@PathVariable Long id, @RequestBody NovoPerfil corpo) {
        if (corpo == null || corpo.perfil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha o novo perfil de acesso.");
        }
        return executar(() -> administracao.alterarPerfil(contexto.atendenteIdAtual(), id, corpo.perfil()));
    }

    @PostMapping("/atendentes/{id}/ativar")
    public AtendenteResumo ativar(@PathVariable Long id) {
        return executar(() -> administracao.ativar(contexto.atendenteIdAtual(), id));
    }

    @PostMapping("/atendentes/{id}/desativar")
    public AtendenteResumo desativar(@PathVariable Long id) {
        return executar(() -> administracao.desativar(contexto.atendenteIdAtual(), id));
    }

    @PutMapping("/atendentes/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definirSenha(@PathVariable Long id, @RequestBody NovaSenha corpo) {
        String senha = corpo == null ? null : corpo.senha();
        executar(() -> {
            Long atendenteId = administracao.buscar(contexto.atendenteIdAtual(), id).id();
            credenciais.definirSenha(atendenteId, senha);
            return null;
        });
    }

    @GetMapping("/responsaveis-notificacao")
    public List<ResponsavelNotificacaoResumo> listarResponsaveis() {
        return executar(() -> responsaveis.listar(contexto.atendenteIdAtual()));
    }

    @PutMapping("/responsaveis-notificacao/{tipo}")
    public List<ResponsavelNotificacaoResumo> definirResponsaveis(@PathVariable TipoNotificacao tipo,
            @RequestBody ListaResponsaveis corpo) {
        if (corpo == null || corpo.atendenteIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a lista de atendentes responsáveis.");
        }
        return executar(() -> responsaveis.definir(contexto.atendenteIdAtual(), tipo, corpo.atendenteIds()));
    }

    private <T> T executar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (AtendenteDuplicadoException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responderErro(ResponseStatusException erro) {
        String mensagem = erro.getReason() == null ? "Não foi possível concluir a operação." : erro.getReason();
        return ResponseEntity.status(erro.getStatusCode()).body(Map.of("mensagem", mensagem));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> responderDadosInvalidos(Exception erro) {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Os dados enviados estão em um formato inválido."));
    }
}