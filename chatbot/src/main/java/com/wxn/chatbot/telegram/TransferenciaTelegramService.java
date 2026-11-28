package com.wxn.chatbot.telegram;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.service.DisponibilidadeAtendimentoService;
import java.time.Instant;

@Service
public class TransferenciaTelegramService {
    static final String CONFIRMACAO = "Sua conversa foi encaminhada para atendimento humano. Aguarde um atendente.";
    private final ConversaRepository conversas;
    private final DisponibilidadeAtendimentoService disponibilidade;
    private final FilaEnvioTelegramService fila;
    public TransferenciaTelegramService(ConversaRepository conversas, DisponibilidadeAtendimentoService disponibilidade,
            FilaEnvioTelegramService fila) {
        this.conversas = conversas;
        this.disponibilidade = disponibilidade;
        this.fila = fila;
    }
    @Transactional
    public String finalizar(Long empresaId, Long conversaId, MensagemTelegram mensagem) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        String resposta = "Seu atendimento já está com a equipe ou na fila. A equipe pode encerrar pelo painel.";
        if (conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            resposta = "Conversa encerrada. Se precisar de ajuda novamente, é só enviar outra mensagem.";
            long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
            conversa.adicionarMensagem(id, resposta, Mensagem.TipoRemetente.CHATBOT);
            conversa.encerrar();
        }
        fila.preparar(empresaId, mensagem.atualizacaoId(), mensagem.chatId(), resposta);
        return resposta;
    }

    @Transactional
    public String solicitar(Long empresaId, Long conversaId) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        String resposta = conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_HUMANO ? CONFIRMACAO
                : disponibilidade.mensagemSolicitacaoHumana(empresaId, Instant.now()).orElse(CONFIRMACAO);
        if (conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) {
            long id = Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0), 1);
            conversa.adicionarMensagem(id, resposta, Mensagem.TipoRemetente.CHATBOT);
            conversa.solicitarAtendimentoHumano();
        }
        if (conversa.getStatus() == Conversa.Status.AGUARDANDO_ATENDENTE || conversa.getStatus() == Conversa.Status.EM_ATENDIMENTO_HUMANO)
            return resposta;
        throw new IllegalStateException("Conversa encerrada.");
    }
}
