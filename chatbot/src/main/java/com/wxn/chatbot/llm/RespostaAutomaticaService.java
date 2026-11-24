package com.wxn.chatbot.llm;

import java.util.LinkedHashSet;
import java.util.List;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.FontesRespostaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RespostaAutomaticaService {
    static final double COBERTURA_SUFICIENTE = 0.6;

    private final ProvedorLlm provedor;
    private final ConversaRepository conversas;
    private final TransactionTemplate transacao;
    private final BuscaConhecimentoService busca;
    private final SegurancaContextoConhecimento seguranca;
    private final DisponibilidadeFonteConhecimento disponibilidade;
    private final PoliticaInsuficiencia politica;
    private final FontesRespostaService fontes;

    public RespostaAutomaticaService(ProvedorLlm provedor, ConversaRepository conversas, PlatformTransactionManager manager,
            BuscaConhecimentoService busca, SegurancaContextoConhecimento seguranca,
            DisponibilidadeFonteConhecimento disponibilidade, PoliticaInsuficiencia politica, FontesRespostaService fontes) {
        this.provedor = provedor;
        this.conversas = conversas;
        this.transacao = new TransactionTemplate(manager);
        this.busca = busca;
        this.seguranca = seguranca;
        this.disponibilidade = disponibilidade;
        this.politica = politica;
        this.fontes = fontes;
    }

    private record Entrada(List<ProvedorLlm.TrechoConversa> historico, String pergunta) { }

    /** IDs devem vir de um contexto de canal validado; este método não autentica clientes. */
    public String responder(Long conversaId, Long empresaId, Long mensagemId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A geração deve ser chamada fora de uma transação.");
        }
        if (conversaId == null || conversaId <= 0 || empresaId == null || empresaId <= 0 || mensagemId == null || mensagemId <= 0)
            throw new IllegalArgumentException("Os identificadores devem ser positivos.");
        Entrada entrada = transacao.execute(status -> {
            Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada nesta empresa."));
            validarAutomatica(conversa);
            return new Entrada(trechos(conversa), ultimaPergunta(conversa));
        });

        ResultadoBusca resultado = busca.buscar(empresaId, entrada.pergunta());
        ContextoConhecimentoSeguro contexto = seguranca.preparar(empresaId, resultado.fontes(), disponibilidade);
        PoliticaInsuficiencia.Decisao decisao = politica.avaliar(
                resultado.situacao() == ResultadoBusca.Situacao.PERGUNTA_SEM_TERMOS,
                avaliarEvidencia(entrada.pergunta(), contexto), contexto);

        if (decisao.acao() != PoliticaInsuficiencia.Acao.RESPONDER) {
            return salvar(conversaId, empresaId, mensagemId, entrada.historico(), null, decisao.mensagem(), List.of(), List.of());
        }

        List<FonteDisponivel> fornecidas = ReferenciasResposta.numerar(contexto.fontes());
        String resposta = provedor.responder(entrada.historico(), fornecidas);
        if (resposta == null || resposta.isBlank()) throw new FalhaLlmException("O provedor retornou uma resposta vazia.");
        ReferenciasResposta.Resultado referencias;
        try {
            referencias = ReferenciasResposta.extrair(resposta, fornecidas);
        } catch (IllegalArgumentException erro) {
            throw new FalhaLlmException("O provedor retornou uma resposta sem texto.");
        }
        if (referencias.texto().replaceAll("\\[[0-9, ]+]", "").isBlank())
            throw new FalhaLlmException("O provedor retornou apenas referências, sem texto.");
        return salvar(conversaId, empresaId, mensagemId, entrada.historico(), contexto, referencias.texto(),
                referencias.citadas(), fornecidas);
    }

    private String salvar(Long conversaId, Long empresaId, Long mensagemId, List<ProvedorLlm.TrechoConversa> historico,
            ContextoConhecimentoSeguro contexto, String texto, List<ReferenciasResposta.Citacao> citadas,
            List<FonteDisponivel> fornecidas) {
        return transacao.execute(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada nesta empresa."));
            validarAutomatica(conversa);
            if (!trechos(conversa).equals(historico)) throw new IllegalStateException("A conversa mudou durante a geração; resposta descartada.");
            if (contexto != null) seguranca.revalidar(contexto, disponibilidade);
            conversa.adicionarMensagem(mensagemId, texto, Mensagem.TipoRemetente.CHATBOT);
            if (!fornecidas.isEmpty()) fontes.registrar(conversa, mensagemId, fornecidas, citadas);
            return texto;
        });
    }

    // A pergunta e suficientemente coberta quando a maior parte dos seus termos aparece nos trechos validados.
    static PoliticaInsuficiencia.Evidencia avaliarEvidencia(String pergunta, ContextoConhecimentoSeguro contexto) {
        if (contexto.fontes().isEmpty()) return PoliticaInsuficiencia.Evidencia.AUSENTE;
        List<String> termos = List.copyOf(new LinkedHashSet<>(BuscaConhecimentoService.termos(pergunta)));
        if (termos.isEmpty()) return PoliticaInsuficiencia.Evidencia.INSUFICIENTE;
        List<String> termosDasFontes = contexto.fontes().stream()
                .flatMap(fonte -> BuscaConhecimentoService.termos(fonte.documentoTitulo() + " "
                        + (fonte.localizacao() == null ? "" : fonte.localizacao()) + " " + fonte.texto()).stream())
                .toList();
        long cobertos = termos.stream()
                .filter(termo -> termosDasFontes.stream().anyMatch(t -> BuscaConhecimentoService.equivalentes(termo, t)))
                .count();
        return (double) cobertos / termos.size() >= COBERTURA_SUFICIENTE
                ? PoliticaInsuficiencia.Evidencia.SUFICIENTE : PoliticaInsuficiencia.Evidencia.INSUFICIENTE;
    }

    private static String ultimaPergunta(Conversa conversa) {
        List<Mensagem> mensagens = conversa.getMensagens();
        for (int i = mensagens.size() - 1; i >= 0; i--) {
            if (mensagens.get(i).getRemetente() == Mensagem.TipoRemetente.CLIENTE) return mensagens.get(i).getConteudo();
        }
        return "";
    }

    private static void validarAutomatica(Conversa conversa) {
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO)
            throw new IllegalStateException("A conversa não está em atendimento automático.");
    }

    private static List<ProvedorLlm.TrechoConversa> trechos(Conversa conversa) {
        return conversa.getMensagens().stream()
                .map(m -> new ProvedorLlm.TrechoConversa(m.getRemetente().name(), m.getConteudo())).toList();
    }
}
