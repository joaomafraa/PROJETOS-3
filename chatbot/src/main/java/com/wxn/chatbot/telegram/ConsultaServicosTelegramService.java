package com.wxn.chatbot.telegram;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wxn.chatbot.model.ServicoContratado;
import com.wxn.chatbot.service.ConsultaServicosContratadosService;
import com.wxn.chatbot.service.ConsultaServicosContratadosService.Item;

// Resposta do comando /planos (HU04.1).
@Service
public class ConsultaServicosTelegramService {

    static final String VALIDACAO_NECESSARIA = "Para consultar seus planos e serviços, valide sua identidade com /validar.";
    static final String SEM_DADOS = "Não há plano ou serviço disponível para consulta no seu cadastro. Se precisar, use /atendente.";
    static final String INDISPONIVEL = "Não foi possível consultar seus planos e serviços agora. "
            + "Tente novamente mais tarde ou use /atendente para pedir ajuda.";
    static final String CABECALHO = "Seus planos e serviços (dados de demonstração):";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConsultaServicosContratadosService consulta;

    public ConsultaServicosTelegramService(ConsultaServicosContratadosService consulta) {
        this.consulta = consulta;
    }

    public String responder(long empresaId, long conversaId) {
        var resultado = consulta.consultar(empresaId, conversaId);
        return switch (resultado.situacao()) {
            case VALIDACAO_NECESSARIA -> VALIDACAO_NECESSARIA;
            case SEM_DADOS -> SEM_DADOS;
            case INDISPONIVEL -> INDISPONIVEL;
            case ENCONTRADO -> resultado.itens().stream().map(ConsultaServicosTelegramService::item)
                    .collect(Collectors.joining("\n\n", CABECALHO + "\n\n", ""));
        };
    }

    static String item(Item item) {
        StringBuilder texto = new StringBuilder("• ").append(item.nome()).append(" (")
                .append(item.tipo() == ServicoContratado.Tipo.PLANO ? "plano" : "serviço").append(") — ")
                .append(situacao(item.situacao()));
        if (item.detalhe() != null) texto.append("\n  ").append(item.detalhe());
        texto.append("\n  Vigência: ").append(item.fim() == null
                ? "desde " + DATA.format(item.inicio())
                : DATA.format(item.inicio()) + " a " + DATA.format(item.fim()));
        return texto.toString();
    }

    private static String situacao(ServicoContratado.Situacao situacao) {
        return switch (situacao) {
            case ATIVO -> "ativo";
            case SUSPENSO -> "suspenso";
            case ENCERRADO -> "encerrado";
        };
    }
}
