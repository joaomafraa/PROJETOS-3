package com.wxn.chatbot.web;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.AtendimentoHumanoService;
import com.wxn.chatbot.telegram.EnvioHumanoTelegramService;
import com.wxn.chatbot.telegram.RevisaoEnvioTelegramService;

import jakarta.persistence.EntityManager;

@RestController
@RequestMapping("/api/conversas")
public class AtendimentoTelegramController {

    private static final String CONVERSA_NAO_ENCONTRADA = "Conversa não encontrada.";
    private static final String OPERACAO_NAO_PERMITIDA = "Operação não permitida.";

    private final EntityManager entityManager;
    private final RevisaoEnvioTelegramService revisao;
    private final ContextoAcesso contexto;
    private final AtendimentoHumanoService atendimento;
    private final EnvioHumanoTelegramService envio;

    public AtendimentoTelegramController(ContextoAcesso contexto, AtendimentoHumanoService atendimento,
            EnvioHumanoTelegramService envio, EntityManager entityManager,
            RevisaoEnvioTelegramService revisao) {
        this.revisao = revisao;
        this.entityManager = entityManager;
        this.contexto = contexto;
        this.atendimento = atendimento;
        this.envio = envio;
    }

    @PostMapping("/{id}/assumir")
    public void assumir(@PathVariable Long id) {
        try {
            atendimento.assumir(id, contexto.atendenteIdAtual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, CONVERSA_NAO_ENCONTRADA);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, OPERACAO_NAO_PERMITIDA);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível assumir a conversa.");
        }
    }

    public record StatusEnvio(Long mensagemId, String status) {
    }

    @GetMapping("/{id}/envios")
    @Transactional(readOnly = true)
    public List<StatusEnvio> envios(@PathVariable Long id) {
        Long empresaId = contexto.empresaIdAtual();
        List<?> linhas = entityManager.createNativeQuery("""
                select e.mensagem_id,
                       case when r.envio_id is not null then 'CONFIRMADO' else e.status end
                  from telegram_envios_atendente e
                  left join telegram_revisoes_envio r on r.envio_id = e.id
                 where e.empresa_id = :empresa
                   and e.conversa_id = :conversa
                """)
                .setParameter("empresa", empresaId)
                .setParameter("conversa", id)
                .getResultList();
        return linhas.stream().map(linha -> {
            Object[] valores = (Object[]) linha;
            return new StatusEnvio(((Number) valores[0]).longValue(), (String) valores[1]);
        }).toList();
    }

    @PostMapping("/{id}/envios/{mensagem}/confirmar")
    public void confirmar(@PathVariable Long id, @PathVariable Long mensagem) {
        try {
            revisao.confirmar(id, mensagem, contexto.atendenteAtual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, CONVERSA_NAO_ENCONTRADA);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, OPERACAO_NAO_PERMITIDA);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Envio já revisado ou indisponível.");
        }
    }

    @PostMapping("/{id}/envios/{mensagem}/reenviar")
    public void reenviar(@PathVariable Long id, @PathVariable Long mensagem) {
        try {
            revisao.reenviar(id, mensagem, contexto.atendenteAtual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, CONVERSA_NAO_ENCONTRADA);
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, OPERACAO_NAO_PERMITIDA);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível confirmar o reenvio.");
        }
    }

    public record Texto(String conteudo) {
    }

    @PostMapping("/{id}/mensagens")
    public void enviar(@PathVariable Long id, @RequestBody Texto texto) {
        try {
            envio.enviar(id, contexto.atendenteAtual(), texto == null ? null : texto.conteudo());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, CONVERSA_NAO_ENCONTRADA);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível preparar a mensagem.");
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, OPERACAO_NAO_PERMITIDA);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Envio sem confirmação; confira antes de tentar novamente.");
        }
    }
}
