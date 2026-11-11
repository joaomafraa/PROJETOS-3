package com.wxn.chatbot.web;

import java.util.List;
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
import com.wxn.chatbot.security.ContextoAcesso;

// API protegida pelo SecurityConfig; a identidade vem do contexto autenticado.
@RestController
@RequestMapping("/api/historico")
@Transactional(readOnly = true)
public class PainelHistoricoController {
    private final ConversaRepository conversas;
    private final HistoricoConversasService historico;
    private final FilaConversasService fila;
    private final ContextoAcesso contextoAcesso;

    public PainelHistoricoController(ConversaRepository conversas,
            HistoricoConversasService historico, FilaConversasService fila,
            ContextoAcesso contextoAcesso) {
        this.conversas = conversas;
        this.historico = historico;
        this.fila = fila;
        this.contextoAcesso = contextoAcesso;
    }

    @GetMapping("/fila")
    public List<ConversaResumo> fila() {
        try {
            return fila.visualizarFila(atendenteDoPainel());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O atendente nao tem permissao para visualizar a fila.");
        }
    }

    @GetMapping("/meus")
    public List<ConversaResumo> meus() {
        Atendente atendente = atendenteDoPainel();
        return conversas.findByEmpresaIdAndResponsavelIdAndStatusOrderByDataInicioAscIdAsc(
                atendente.getEmpresa().getId(), atendente.getId(), com.wxn.chatbot.model.Conversa.Status.EM_ATENDIMENTO_HUMANO)
                .stream().map(c -> new ConversaResumo(c.getId(), c.getCliente().getId(), c.getCliente().getNome(),
                        c.getCliente().getNumeroTelefone(), c.getChatbot().getNome(), c.getDataInicio(), c.getStatus())).toList();
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
        return contextoAcesso.atendenteAtual();
    }

}
