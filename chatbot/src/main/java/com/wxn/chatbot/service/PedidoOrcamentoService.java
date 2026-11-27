package com.wxn.chatbot.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.PedidoOrcamentoRegistrado;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.OperacaoSolicitacao;
import com.wxn.chatbot.model.PedidoOrcamento;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.PedidoOrcamentoRepository;

/**
 * Registro de pedidos de orcamento (HU06.1).
 *
 * O pedido e um Ticket do tipo ORCAMENTO, com o mesmo gerador de protocolo (PD-), a mesma
 * protecao contra repeticao e o mesmo aviso aos responsaveis dos demais pedidos. A necessidade
 * fica na descricao do ticket e o contato para retorno em {@link PedidoOrcamento}.
 *
 * Nao cria lead: ligar um pedido a um lead exige uma regra combinada com a equipe.
 * Nao calcula preco nem promete proposta: so registra o pedido para a equipe comercial.
 */
@Service
public class PedidoOrcamentoService {
    public static final int NECESSIDADE_MINIMA = 10;
    public static final int NECESSIDADE_MAXIMA = 2000;

    private final AbrirChamadoService registro;
    private final PedidoOrcamentoRepository pedidos;
    private final ConversaRepository conversas;

    public PedidoOrcamentoService(AbrirChamadoService registro, PedidoOrcamentoRepository pedidos,
            ConversaRepository conversas) {
        this.registro = Objects.requireNonNull(registro);
        this.pedidos = Objects.requireNonNull(pedidos);
        this.conversas = Objects.requireNonNull(conversas);
    }

    /**
     * Contato que o cliente da conversa ja deixou no cadastro. Vazio quando nao ha telefone nem
     * e-mail: so nesse caso o canal precisa pedir um contato ao cliente.
     */
    @Transactional(readOnly = true)
    public Optional<String> contatoCadastrado(Long empresaId, Long conversaId) {
        Conversa conversa = conversas.buscarParaAbrirChamado(conversaId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa nao encontrada nesta empresa."));
        return PedidoOrcamento.contatoDoCadastro(conversa.getCliente());
    }

    /**
     * Registra o pedido de forma idempotente.
     *
     * @param empresaId empresa do canal validado (nunca um valor informado livremente pelo cliente)
     * @param contatoInformado e-mail ou telefone digitado pelo cliente; {@code null} usa o contato do cadastro
     * @param chaveOperacao identifica a operacao de origem, ex.: {@code "telegram:" + atualizacaoId};
     *        a mesma chave devolve o mesmo pedido, uma chave nova cria outro pedido
     */
    @Transactional
    public PedidoOrcamentoRegistrado registrar(Long empresaId, Long conversaId, String necessidade,
            String contatoInformado, String chaveOperacao) {
        String necessidadeLimpa = validarNecessidade(necessidade);
        OperacaoSolicitacao.validarChave(chaveOperacao);
        String contato = contatoInformado == null || contatoInformado.isBlank()
                ? contatoCadastrado(empresaId, conversaId).orElseThrow(
                        () -> new IllegalArgumentException("Informe um e-mail ou telefone para retorno."))
                : PedidoOrcamento.contatoInformado(contatoInformado);

        AbrirChamadoService.Registro resultado = registro.registrar(empresaId, conversaId,
                Ticket.TipoSolicitacao.ORCAMENTO, necessidadeLimpa, chaveOperacao);
        Ticket ticket = resultado.ticket();
        PedidoOrcamento pedido = resultado.novo()
                ? pedidos.saveAndFlush(new PedidoOrcamento(ticket, contato))
                : pedidos.findByTicketIdAndEmpresaId(ticket.getId(), empresaId).orElseThrow(
                        () -> new IllegalStateException("O pedido registrado nao tem contato de orcamento."));

        return new PedidoOrcamentoRegistrado(ticket.getId(), ticket.getProtocolo(), ticket.getTipo(),
                ticket.getStatus(), ticket.getDescricao(), pedido.getContato(), ticket.getDataAbertura(),
                ticket.getEmpresa().getId(), ticket.getCliente().getId(), ticket.getConversaOrigem().getId(),
                resultado.novo());
    }

    private static String validarNecessidade(String necessidade) {
        if (necessidade == null) {
            throw new IllegalArgumentException("Descreva o que voce precisa orcar.");
        }
        String limpa = necessidade.strip();
        if (limpa.length() < NECESSIDADE_MINIMA || limpa.length() > NECESSIDADE_MAXIMA) {
            throw new IllegalArgumentException("A necessidade deve ter de " + NECESSIDADE_MINIMA
                    + " a " + NECESSIDADE_MAXIMA + " caracteres.");
        }
        return limpa;
    }
}
