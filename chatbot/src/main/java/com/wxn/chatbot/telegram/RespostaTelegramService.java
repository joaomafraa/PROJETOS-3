package com.wxn.chatbot.telegram;

import com.wxn.chatbot.llm.RespostaAutomaticaService;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Geração da resposta para uma conversa já registrada; não envia nem confirma atualizações. */
@Service
public class RespostaTelegramService {
    private final ConversaRepository conversas;
    private final RespostaAutomaticaService respostas;
    private final TransactionTemplate transacao;

    public RespostaTelegramService(ConversaRepository conversas, RespostaAutomaticaService respostas,
                                    PlatformTransactionManager manager) {
        this.conversas = conversas;
        this.respostas = respostas;
        this.transacao = new TransactionTemplate(manager);
    }

    public String respostaExistenteOuPendente(Long empresaId, Long conversaId) {
        return transacao.execute(status -> {
            Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));
            if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) return null;
            var mensagens = conversa.getMensagens();
            if (!mensagens.isEmpty()) {
                Mensagem ultima = mensagens.get(mensagens.size() - 1);
                if (ultima.getRemetente() == Mensagem.TipoRemetente.CHATBOT) return ultima.getConteudo();
            }
            return "";
        });
    }

    public String gerar(Long empresaId, Long conversaId) {
        if (empresaId == null || empresaId <= 0 || conversaId == null || conversaId <= 0)
            throw new IllegalArgumentException("Empresa e conversa são obrigatórias.");
        Long proximoId = transacao.execute(status -> {
            Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));
            if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO)
                throw new IllegalStateException("A conversa não está em atendimento automático.");
            return Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
        });
        return respostas.responder(conversaId, empresaId, proximoId);
    }
}
