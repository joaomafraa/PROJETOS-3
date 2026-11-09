package com.wxn.chatbot.service;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.TicketRepository;

@Service
public class AbrirChamadoService {

    private final ConversaRepository conversas;
    private final TicketRepository tickets;

    public AbrirChamadoService(ConversaRepository conversas, TicketRepository tickets) {
        this.conversas = Objects.requireNonNull(conversas, "O repositorio de conversas e obrigatorio.");
        this.tickets = Objects.requireNonNull(tickets, "O repositorio de tickets e obrigatorio.");
    }

    /**
     * Abre um chamado de suporte a partir de uma conversa existente.
     * O empresaId deve vir do contexto validado do canal ou da identidade autenticada.
     */
    @Transactional
    public TicketAberto abrir(Long empresaId, Long conversaId, String descricao) {
        validarId(empresaId, "empresa");
        validarId(conversaId, "conversa");

        Conversa conversa = conversas.buscarParaAbrirChamado(conversaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa nao encontrada nesta empresa."));
        validarRelacionamentos(conversa, empresaId);

        Identificacao identificacao = gerarIdentificacao();
        Ticket ticket = new Ticket(identificacao.id(), identificacao.protocolo(), descricao,
                Ticket.TipoSolicitacao.SUPORTE, conversa);
        tickets.saveAndFlush(ticket);

        return new TicketAberto(ticket.getId(), ticket.getProtocolo(), ticket.getDescricao(),
                ticket.getTipo(), ticket.getStatus(), ticket.getDataAbertura(),
                ticket.getEmpresa().getId(), ticket.getCliente().getId(),
                ticket.getConversaOrigem().getId());
    }

    private void validarRelacionamentos(Conversa conversa, Long empresaId) {
        if (!conversa.getEmpresa().getId().equals(empresaId)) {
            throw new SecurityException("A conversa nao pertence a empresa informada.");
        }
        if (conversa.getCliente().getEmpresa() == null
                || !conversa.getCliente().getEmpresa().getId().equals(empresaId)) {
            throw new IllegalStateException("O cliente da conversa nao pertence a empresa.");
        }
        if (!conversa.getChatbot().pertenceA(conversa.getEmpresa())) {
            throw new IllegalStateException("O chatbot da conversa nao pertence a empresa.");
        }
        if (conversa.getResponsavel() != null
                && !conversa.getResponsavel().pertenceA(conversa.getEmpresa())) {
            throw new IllegalStateException("O responsavel da conversa nao pertence a empresa.");
        }
    }

    private Identificacao gerarIdentificacao() {
        while (true) {
            Long id = tickets.proximoId();
            String protocolo = String.format(Locale.ROOT, "CH-%020d", id);
            if (!tickets.existsById(id) && !tickets.existsByProtocolo(protocolo)) {
                return new Identificacao(id, protocolo);
            }
        }
    }

    private static void validarId(Long id, String campo) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O id de " + campo + " deve ser positivo.");
        }
    }

    private record Identificacao(Long id, String protocolo) {
    }
}
