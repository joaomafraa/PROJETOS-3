package com.wxn.chatbot.telegram;

import java.util.List;
import com.wxn.chatbot.dto.FontesMensagem;
import com.wxn.chatbot.llm.RespostaAutomaticaService;
import com.wxn.chatbot.service.FontesRespostaService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final FontesRespostaService fontes;

    public RespostaTelegramService(ConversaRepository conversas, RespostaAutomaticaService respostas,
                                    PlatformTransactionManager manager) {
        this(conversas, respostas, manager, null);
    }

    @Autowired
    public RespostaTelegramService(ConversaRepository conversas, RespostaAutomaticaService respostas,
                                    PlatformTransactionManager manager, FontesRespostaService fontes) {
        this.conversas = conversas;
        this.respostas = respostas;
        this.transacao = new TransactionTemplate(manager);
        this.fontes = fontes;
    }

    // Ultima mensagem da conversa automatica; id nulo quando ainda nao ha resposta do chatbot.
    private record Ultima(Long id, String conteudo) { }

    public String respostaExistenteOuPendente(Long empresaId, Long conversaId) {
        Ultima resposta = transacao.execute(status -> {
            Conversa conversa = conversas.findByIdAndEmpresaId(conversaId, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));
            if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) return null;
            var mensagens = conversa.getMensagens();
            if (!mensagens.isEmpty()) {
                Mensagem ultima = mensagens.get(mensagens.size() - 1);
                if (ultima.getRemetente() == Mensagem.TipoRemetente.CHATBOT) return new Ultima(ultima.getId(), ultima.getConteudo());
            }
            return new Ultima(null, "");
        });
        if (resposta == null) return null;
        if (resposta.id() == null) return "";
        return paraTelegram(empresaId, conversaId, resposta.id(), resposta.conteudo());
    }

    // HU02.2: texto legivel e lista de fontes citadas, dentro do limite do Telegram.
    private String paraTelegram(Long empresaId, Long conversaId, Long mensagemId, String texto) {
        var citadas = fontes == null ? List.<FontesMensagem.Fonte>of() : fontes.citadas(empresaId, conversaId, mensagemId);
        return FormatadorRespostaTelegram.formatar(texto, citadas);
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
        String texto = respostas.responder(conversaId, empresaId, proximoId);
        return paraTelegram(empresaId, conversaId, proximoId, texto);
    }
}
