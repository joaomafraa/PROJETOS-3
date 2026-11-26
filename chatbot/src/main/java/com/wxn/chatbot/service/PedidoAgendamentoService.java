package com.wxn.chatbot.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.PedidoAgendamentoRegistrado;
import com.wxn.chatbot.model.OperacaoSolicitacao;
import com.wxn.chatbot.model.PreferenciaAgendamento;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.PreferenciaAgendamentoRepository;

/**
 * Registro de pedidos de demonstracao, reuniao e visita tecnica (HU06.2).
 *
 * O pedido e um Ticket com protocolo proprio, ligado a conversa, ao cliente e a
 * empresa. A preferencia de horario e guardada a parte e apresentada como preferencia.
 * O registro acontece antes de qualquer confirmacao ou notificacao.
 */
@Service
public class PedidoAgendamentoService {
    public static final int NECESSIDADE_MINIMA = 10;
    public static final int NECESSIDADE_MAXIMA = 2000;

    private final AbrirChamadoService registro;
    private final PreferenciaAgendamentoRepository preferencias;

    public PedidoAgendamentoService(AbrirChamadoService registro, PreferenciaAgendamentoRepository preferencias) {
        this.registro = Objects.requireNonNull(registro);
        this.preferencias = Objects.requireNonNull(preferencias);
    }

    /**
     * Registra o pedido de forma idempotente.
     *
     * @param empresaId empresa do canal validado (nunca um valor informado livremente pelo cliente)
     * @param chaveOperacao identifica a operacao de origem, ex.: {@code "telegram:" + atualizacaoId};
     *        a mesma chave devolve o mesmo pedido, uma chave nova cria outro pedido
     */
    @Transactional
    public PedidoAgendamentoRegistrado registrar(Long empresaId, Long conversaId, Ticket.TipoSolicitacao tipo,
            String necessidade, String preferenciaHorario, String chaveOperacao) {
        Objects.requireNonNull(tipo, "O tipo do pedido e obrigatorio.");
        if (!PreferenciaAgendamento.TIPOS_COM_PREFERENCIA.contains(tipo)) {
            throw new IllegalArgumentException("O pedido deve ser de demonstracao, reuniao ou visita tecnica.");
        }
        String necessidadeLimpa = validarNecessidade(necessidade);
        String preferenciaLimpa = PreferenciaAgendamento.validar(preferenciaHorario);
        OperacaoSolicitacao.validarChave(chaveOperacao);

        AbrirChamadoService.Registro resultado = registro.registrar(empresaId, conversaId, tipo, necessidadeLimpa,
                chaveOperacao);
        Ticket pedido = resultado.ticket();
        PreferenciaAgendamento preferencia = resultado.novo()
                ? preferencias.saveAndFlush(new PreferenciaAgendamento(pedido, preferenciaLimpa))
                : preferencias.findByTicketIdAndEmpresaId(pedido.getId(), empresaId)
                        .orElseThrow(() -> new IllegalStateException("O pedido registrado nao tem preferencia de horario."));

        return new PedidoAgendamentoRegistrado(pedido.getId(), pedido.getProtocolo(), pedido.getTipo(),
                pedido.getStatus(), pedido.getDescricao(), preferencia.getPreferenciaHorario(),
                pedido.getDataAbertura(), pedido.getEmpresa().getId(), pedido.getCliente().getId(),
                pedido.getConversaOrigem().getId(), resultado.novo());
    }

    private static String validarNecessidade(String necessidade) {
        if (necessidade == null) {
            throw new IllegalArgumentException("Descreva a necessidade do pedido.");
        }
        String limpa = necessidade.strip();
        if (limpa.length() < NECESSIDADE_MINIMA || limpa.length() > NECESSIDADE_MAXIMA) {
            throw new IllegalArgumentException("A necessidade deve ter de " + NECESSIDADE_MINIMA
                    + " a " + NECESSIDADE_MAXIMA + " caracteres.");
        }
        return limpa;
    }
}
