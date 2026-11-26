package com.wxn.chatbot.service;

import java.util.Locale;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.OperacaoSolicitacao;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.OperacaoSolicitacaoRepository;
import com.wxn.chatbot.repository.TicketRepository;

@Service
public class AbrirChamadoService {

    private final ConversaRepository conversas;
    private final TicketRepository tickets;
    private final ApplicationEventPublisher eventos;
    private final OperacaoSolicitacaoRepository operacoes;

    public AbrirChamadoService(ConversaRepository conversas, TicketRepository tickets,
            ApplicationEventPublisher eventos, OperacaoSolicitacaoRepository operacoes) {
        this.conversas = Objects.requireNonNull(conversas, "O repositorio de conversas e obrigatorio.");
        this.tickets = Objects.requireNonNull(tickets, "O repositorio de tickets e obrigatorio.");
        this.eventos = Objects.requireNonNull(eventos, "O publicador de eventos e obrigatorio.");
        this.operacoes = Objects.requireNonNull(operacoes, "O repositorio de operacoes e obrigatorio.");
    }

    /**
     * Abre um chamado de suporte a partir de uma conversa existente.
     * O empresaId deve vir do contexto validado do canal ou da identidade autenticada.
     * Sem chave da operacao, cada chamada cria um chamado novo; prefira a versao com chave.
     */
    @Transactional
    public TicketAberto abrir(Long empresaId, Long conversaId, String descricao) {
        return resumir(registrar(empresaId, conversaId, Ticket.TipoSolicitacao.SUPORTE, descricao, null).ticket());
    }

    /**
     * Abre um chamado de suporte de forma idempotente (HU05.1/HU05.2).
     * A chave identifica a operacao de origem, por exemplo {@code "telegram:" + atualizacaoId}.
     * Repetir a mesma chave devolve o mesmo chamado e o mesmo protocolo, sem criar outro.
     */
    @Transactional
    public TicketAberto abrir(Long empresaId, Long conversaId, String descricao, String chaveOperacao) {
        OperacaoSolicitacao.validarChave(chaveOperacao);
        return resumir(registrar(empresaId, conversaId, Ticket.TipoSolicitacao.SUPORTE, descricao, chaveOperacao).ticket());
    }

    /** Resultado do registro; {@code novo} e falso quando a operacao ja tinha sido registrada. */
    public record Registro(Ticket ticket, boolean novo) {
    }

    /**
     * Registra uma solicitacao de qualquer tipo com as mesmas regras de escopo e o mesmo
     * gerador de protocolo dos chamados. Tambem usado pelos pedidos de agendamento (HU06.2).
     *
     * Com chave: a operacao repetida devolve a solicitacao ja salva. Duas transacoes
     * simultaneas com a mesma chave nao geram duas solicitacoes: a segunda falha na
     * chave primaria de operacoes_solicitacao e pode ser reprocessada.
     */
    @Transactional
    public Registro registrar(Long empresaId, Long conversaId, Ticket.TipoSolicitacao tipo, String descricao,
            String chaveOperacao) {
        validarId(empresaId, "empresa");
        validarId(conversaId, "conversa");
        Objects.requireNonNull(tipo, "O tipo da solicitacao e obrigatorio.");
        String chave = chaveOperacao == null ? null : OperacaoSolicitacao.validarChave(chaveOperacao);

        if (chave != null) {
            var anterior = operacoes.findById(new OperacaoSolicitacao.Chave(empresaId, chave));
            if (anterior.isPresent()) {
                Ticket existente = anterior.get().getTicket();
                if (!existente.getConversaOrigem().getId().equals(conversaId) || existente.getTipo() != tipo) {
                    throw new IllegalStateException("A chave da operacao ja foi usada para outra solicitacao.");
                }
                return new Registro(existente, false);
            }
        }

        Conversa conversa = conversas.buscarParaAbrirChamado(conversaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversa nao encontrada nesta empresa."));
        validarRelacionamentos(conversa, empresaId);

        Identificacao identificacao = gerarIdentificacao(tipo);
        Ticket ticket = new Ticket(identificacao.id(), identificacao.protocolo(), descricao, tipo, conversa);
        tickets.saveAndFlush(ticket);
        if (chave != null) {
            operacoes.saveAndFlush(new OperacaoSolicitacao(chave, ticket));
        }
        // HU05.3: os responsaveis so sao avisados depois que esta transacao fizer commit.
        eventos.publishEvent(new SolicitacaoRegistradaEvento(ticket.getId(), empresaId));
        return new Registro(ticket, true);
    }

    private static TicketAberto resumir(Ticket ticket) {
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

    // Mesmo gerador para todas as solicitacoes: CH- para chamados de suporte e
    // PD- para pedidos (orcamento, demonstracao, reuniao, visita). O id vem da sequencia.
    private Identificacao gerarIdentificacao(Ticket.TipoSolicitacao tipo) {
        String prefixo = tipo == Ticket.TipoSolicitacao.SUPORTE ? "CH" : "PD";
        while (true) {
            Long id = tickets.proximoId();
            String protocolo = String.format(Locale.ROOT, "%s-%020d", prefixo, id);
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
