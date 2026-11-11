package com.wxn.chatbot.llm;

import java.util.List;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RespostaAutomaticaService {
    private final ProvedorLlm provedor;
    private final ConversaRepository conversas;
    private final TransactionTemplate transacao;

    public RespostaAutomaticaService(ProvedorLlm provedor, ConversaRepository conversas, PlatformTransactionManager manager) {
        this.provedor = provedor;
        this.conversas = conversas;
        this.transacao = new TransactionTemplate(manager);
    }

    /** IDs devem vir de um contexto de canal validado; este método não autentica clientes. */
    public String responder(Long conversaId, Long empresaId, Long mensagemId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A geração deve ser chamada fora de uma transação.");
        }
        if (conversaId == null || conversaId <= 0 || empresaId == null || empresaId <= 0 || mensagemId == null || mensagemId <= 0)
            throw new IllegalArgumentException("Os identificadores devem ser positivos.");
        List<ProvedorLlm.TrechoConversa> historico = transacao.execute(status -> {
            Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada nesta empresa."));
            validarAutomatica(conversa);
            return trechos(conversa);
        });
        String resposta = provedor.responder(historico);
        if (resposta == null || resposta.isBlank()) throw new FalhaLlmException("O provedor retornou uma resposta vazia.");
        return transacao.execute(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada nesta empresa."));
            validarAutomatica(conversa);
            if (!trechos(conversa).equals(historico)) throw new IllegalStateException("A conversa mudou durante a geração; resposta descartada.");
            conversa.adicionarMensagem(mensagemId, resposta, Mensagem.TipoRemetente.CHATBOT);
            return resposta;
        });
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
