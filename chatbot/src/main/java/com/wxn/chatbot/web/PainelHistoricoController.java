package com.wxn.chatbot.web;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wxn.chatbot.dto.ConversaResumo;
import com.wxn.chatbot.dto.MensagemResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.HistoricoConversasService;
import com.wxn.chatbot.service.FilaConversasService;

// Modo de apresentacao local; nao substitui autenticacao em producao.
@Profile("painel-local")
@RestController
@RequestMapping("/api/historico")
@Transactional(readOnly = true)
public class PainelHistoricoController {
    private final EntityManager entityManager;
    private final ConversaRepository conversas;
    private final HistoricoConversasService historico;
    private final FilaConversasService fila;
    private final Long atendenteId;

    public PainelHistoricoController(EntityManager entityManager, ConversaRepository conversas,
            HistoricoConversasService historico, FilaConversasService fila,
            @Value("${painel.atendente-id:1}") Long atendenteId) {
        this.entityManager = entityManager;
        this.conversas = conversas;
        this.historico = historico;
        this.fila = fila;
        this.atendenteId = atendenteId;
    }

    @GetMapping("/fila")
    public List<ConversaResumo> fila() {
        return fila.visualizarFila(atendenteDoPainel());
    }

    @GetMapping
    public List<ConversaResumo> listar() {
        Atendente atendente = atendenteDoPainel();
        return conversas.findTop100ByEmpresaIdOrderByDataInicioDescIdDesc(atendente.getEmpresa().getId())
                .stream().map(conversa -> new ConversaResumo(conversa.getId(), conversa.getCliente().getId(),
                        conversa.getCliente().getNome(), conversa.getCliente().getNumeroTelefone(),
                        conversa.getChatbot().getNome(), conversa.getDataInicio(), conversa.getStatus())).toList();
    }

    @GetMapping("/{conversaId}/mensagens")
    public List<MensagemResumo> mensagens(@PathVariable Long conversaId) {
        Atendente atendente = atendenteDoPainel();
        try {
            return historico.visualizarHistorico(conversaId, atendente);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa nao encontrada.");
        }
    }

    private Atendente atendenteDoPainel() {
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (atendente == null || !atendente.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Configure um atendente ativo para o painel local.");
        }
        return atendente;
    }
}
