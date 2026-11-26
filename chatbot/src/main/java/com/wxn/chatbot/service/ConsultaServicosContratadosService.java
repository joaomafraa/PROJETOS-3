package com.wxn.chatbot.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wxn.chatbot.model.ServicoContratado;
import com.wxn.chatbot.repository.ServicoContratadoRepository;
import com.wxn.chatbot.security.ValidacaoClienteService;

// HU04.1: o cliente validado consulta apenas os planos e servicos ficticios dele, na empresa do canal.
@Service
public class ConsultaServicosContratadosService {

    public static final int MAXIMO_ITENS = 20;
    private static final Logger log = LoggerFactory.getLogger(ConsultaServicosContratadosService.class);

    public enum Situacao { ENCONTRADO, SEM_DADOS, VALIDACAO_NECESSARIA, INDISPONIVEL }

    public record Item(ServicoContratado.Tipo tipo, String nome, String detalhe, ServicoContratado.Situacao situacao,
            LocalDate inicio, LocalDate fim) { }

    public record Resultado(Situacao situacao, List<Item> itens) {
        public Resultado {
            Objects.requireNonNull(situacao, "A situacao e obrigatoria.");
            itens = itens == null ? List.of() : List.copyOf(itens);
            if ((situacao == Situacao.ENCONTRADO) == itens.isEmpty())
                throw new IllegalArgumentException("Somente o resultado ENCONTRADO tem itens, e precisa ter ao menos um.");
        }

        static Resultado vazio(Situacao situacao) { return new Resultado(situacao, List.of()); }
    }

    private final ValidacaoClienteService validacao;
    private final ServicoContratadoRepository servicos;

    public ConsultaServicosContratadosService(ValidacaoClienteService validacao, ServicoContratadoRepository servicos) {
        this.validacao = validacao;
        this.servicos = servicos;
    }

    // empresaId e conversaId vem do canal validado; o cliente sai da validacao, nunca do pedido.
    public Resultado consultar(long empresaId, long conversaId) {
        ValidacaoClienteService.ContextoClienteValidado contexto;
        try {
            contexto = validacao.exigirContextoValidado(empresaId, conversaId);
        } catch (SecurityException semValidacao) {
            return Resultado.vazio(Situacao.VALIDACAO_NECESSARIA);
        } catch (RuntimeException erro) {
            log.warn("Consulta de servicos indisponivel na validacao: {}", erro.getClass().getSimpleName());
            return Resultado.vazio(Situacao.INDISPONIVEL);
        }
        List<ServicoContratado> encontrados;
        try {
            encontrados = servicos.doCliente(contexto.empresaId(), contexto.clienteId());
        } catch (RuntimeException erro) {
            log.warn("Consulta de servicos indisponivel: {}", erro.getClass().getSimpleName());
            return Resultado.vazio(Situacao.INDISPONIVEL);
        }
        if (encontrados.isEmpty()) return Resultado.vazio(Situacao.SEM_DADOS);
        return new Resultado(Situacao.ENCONTRADO, encontrados.stream().limit(MAXIMO_ITENS)
                .map(s -> new Item(s.getTipo(), s.getNome(), s.getDetalhe(), s.getSituacao(), s.getInicio(), s.getFim()))
                .toList());
    }
}
