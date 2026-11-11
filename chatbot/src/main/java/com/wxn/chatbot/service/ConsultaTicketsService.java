package com.wxn.chatbot.service;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.dto.PaginaResumo;
import com.wxn.chatbot.dto.ResponsavelOpcao;
import com.wxn.chatbot.dto.TicketDetalhe;
import com.wxn.chatbot.dto.TicketResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.AtendenteRepository;
import com.wxn.chatbot.repository.TicketRepository;
import com.wxn.chatbot.repository.TicketSpecifications;

// Consulta de solicitacoes (HU10.1 e HU10.2): listagem com filtros e paginacao
// e detalhes com solicitante, conversa de origem e historico.
// Sempre limitada a empresa do atendente informado.
@Service
public class ConsultaTicketsService {
    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    // Mais recentes primeiro; o id desempata tickets abertos no mesmo instante.
    private static final Sort ORDEM = Sort.by(Sort.Order.desc("dataAbertura"), Sort.Order.desc("id"));

    private final TicketRepository ticketRepository;
    private final HistoricoConversasService historicoService;
    private final AtendenteRepository atendenteRepository;

    public ConsultaTicketsService(TicketRepository ticketRepository,
            HistoricoConversasService historicoService, AtendenteRepository atendenteRepository) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.historicoService = Objects.requireNonNull(historicoService);
        this.atendenteRepository = Objects.requireNonNull(atendenteRepository);
    }

    // Opcoes do filtro por responsavel: atendentes da mesma empresa, inclusive inativos,
    // porque tickets antigos podem continuar com eles como responsaveis.
    @Transactional(readOnly = true)
    public List<ResponsavelOpcao> listarResponsaveis(Atendente atendente) {
        verificarAtendente(atendente);
        return atendenteRepository.findByEmpresaIdOrderByNomeAscIdAsc(atendente.getEmpresa().getId()).stream()
                .map(opcao -> new ResponsavelOpcao(opcao.getId(), opcao.getNome(), opcao.isAtivo(),
                        opcao.getId().equals(atendente.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaResumo<TicketResumo> listar(Atendente atendente, FiltroTickets filtro,
            int pagina, int tamanho) {
        verificarAtendente(atendente);
        if (pagina < 0) {
            throw new IllegalArgumentException("A pagina deve ser zero ou maior.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new IllegalArgumentException(
                    "O tamanho da pagina deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA + ".");
        }
        FiltroTickets criterios = filtro == null ? FiltroTickets.nenhum() : filtro;

        Page<Ticket> resultado = ticketRepository.findAll(
                TicketSpecifications.daEmpresaComFiltro(atendente.getEmpresa().getId(), criterios),
                PageRequest.of(pagina, tamanho, ORDEM));

        return new PaginaResumo<>(resultado.getContent().stream().map(this::resumir).toList(),
                resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TicketDetalhe detalhar(Long ticketId, Atendente atendente) {
        verificarAtendente(atendente);
        if (ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("O id do ticket deve ser positivo.");
        }
        Ticket ticket = ticketRepository.findByIdAndEmpresaId(ticketId, atendente.getEmpresa().getId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket nao encontrado nesta empresa."));

        Cliente cliente = ticket.getCliente();
        Conversa conversa = ticket.getConversaOrigem();
        Atendente responsavel = ticket.getResponsavel();

        return new TicketDetalhe(ticket.getId(), ticket.getProtocolo(), ticket.getDescricao(),
                ticket.getTipo(), ticket.getStatus(), ticket.getDataAbertura(), ticket.getDataConclusao(),
                responsavel == null ? null : responsavel.getId(),
                responsavel == null ? null : responsavel.getNome(),
                new TicketDetalhe.Solicitante(cliente.getId(), cliente.getNome(), cliente.getEmail(),
                        cliente.getNumeroTelefone(), cliente.isIdentidadeConfirmada()),
                new TicketDetalhe.ConversaOrigem(conversa.getId(), conversa.getStatus(),
                        conversa.getDataInicio(), conversa.getDataEncerramento(),
                        conversa.getChatbot().getNome()),
                historicoService.visualizarHistorico(conversa.getId(), atendente));
    }

    private void verificarAtendente(Atendente atendente) {
        Objects.requireNonNull(atendente, "O atendente e obrigatorio.");
        if (!atendente.isAtivo()) {
            throw new SecurityException("Um atendente inativo nao pode consultar solicitacoes.");
        }
    }

    private TicketResumo resumir(Ticket ticket) {
        Atendente responsavel = ticket.getResponsavel();
        return new TicketResumo(ticket.getId(), ticket.getProtocolo(), ticket.getTipo(), ticket.getStatus(),
                ticket.getDataAbertura(), ticket.getCliente().getId(), ticket.getCliente().getNome(),
                responsavel == null ? null : responsavel.getId(),
                responsavel == null ? null : responsavel.getNome());
    }
}
