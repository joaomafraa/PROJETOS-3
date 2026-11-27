package com.wxn.chatbot.dto;

import java.time.Instant;
import java.util.List;

import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;

// Detalhes de uma solicitacao: dados do ticket, do solicitante,
// da conversa de origem e o historico dessa conversa.
// preferenciaHorario: so em demonstracao, reuniao e visita (HU06.2); e preferencia, nao horario confirmado.
// contatoOrcamento: so em orcamento (HU06.1); contato para a equipe comercial retornar.
public record TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
        Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
        Long responsavelId, String responsavelNome, Solicitante solicitante,
        ConversaOrigem conversaOrigem, List<MensagemResumo> historico, String preferenciaHorario,
        String contatoOrcamento) {

    public TicketDetalhe {
        historico = List.copyOf(historico);
    }

    // Formato anterior, sem contato de orcamento.
    public TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
            Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
            Long responsavelId, String responsavelNome, Solicitante solicitante,
            ConversaOrigem conversaOrigem, List<MensagemResumo> historico, String preferenciaHorario) {
        this(id, protocolo, descricao, tipo, status, dataAbertura, dataConclusao, responsavelId, responsavelNome,
                solicitante, conversaOrigem, historico, preferenciaHorario, null);
    }

    // Formato anterior, sem preferencia de horario.
    public TicketDetalhe(Long id, String protocolo, String descricao, Ticket.TipoSolicitacao tipo,
            Ticket.StatusTicket status, Instant dataAbertura, Instant dataConclusao,
            Long responsavelId, String responsavelNome, Solicitante solicitante,
            ConversaOrigem conversaOrigem, List<MensagemResumo> historico) {
        this(id, protocolo, descricao, tipo, status, dataAbertura, dataConclusao, responsavelId, responsavelNome,
                solicitante, conversaOrigem, historico, null, null);
    }

    public record Solicitante(Long id, String nome, String email, String numeroTelefone,
            boolean identidadeConfirmada) {
    }

    public record ConversaOrigem(Long id, Conversa.Status status, Instant dataInicio,
            Instant dataEncerramento, String chatbotNome) {
    }
}
