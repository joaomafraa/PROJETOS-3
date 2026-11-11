package com.wxn.chatbot.telegram;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

@Service
public class TransferenciaTelegramService {
    static final String CONFIRMACAO = "Sua conversa foi encaminhada para atendimento humano. Aguarde um atendente.";
    private final ConversaRepository conversas;
    public TransferenciaTelegramService(ConversaRepository conversas) { this.conversas = conversas; }
    @Transactional
    public String solicitar(Long empresaId, Long conversaId) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        if (conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
            conversa.adicionarMensagem(id, CONFIRMACAO, Mensagem.TipoRemetente.CHATBOT);
            conversa.solicitarAtendimentoHumano();
        }
        if (conversa.getStatus() == Conversa.Status.AGUARDANDO_ATENDENTE || conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_HUMANO)
            return CONFIRMACAO;
        throw new IllegalStateException("Conversa encerrada.");
    }
}
