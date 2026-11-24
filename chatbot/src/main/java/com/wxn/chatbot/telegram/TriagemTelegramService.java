package com.wxn.chatbot.telegram;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

/** Classificação conservadora, sem criar leads ou chamados automaticamente. */
@Service
public class TriagemTelegramService {
    private final ConversaRepository conversas;
    private final FilaEnvioTelegramService fila;
    public TriagemTelegramService(ConversaRepository conversas, FilaEnvioTelegramService fila) {
        this.conversas = conversas; this.fila = fila;
    }
    @Transactional
    public Optional<String> atender(long empresaId, long conversaId, MensagemTelegram mensagem) {
        Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
        if (conversa.getStatus() != Conversa.Status.EM_ATENDIMENTO_AUTOMATICO) return Optional.empty();
        String texto = normalizar(mensagem.texto());
        boolean escolhaSuporte = texto.equals("/suporte") || texto.equals("suporte");
        boolean escolhaComercial = texto.equals("/comercial") || texto.equals("comercial");
        if (escolhaSuporte || escolhaComercial) {
            conversa.definirIntencao(escolhaSuporte ? Conversa.Intencao.SUPORTE : Conversa.Intencao.COMERCIAL);
            return preparar(conversa, mensagem, escolhaSuporte
                    ? "Certo, seu atendimento foi classificado como suporte. Conte como posso ajudar."
                    : "Certo, seu atendimento foi classificado como comercial. Conte como posso ajudar.");
        }
        if (conversa.getIntencao() != Conversa.Intencao.INDEFINIDA) return Optional.empty();
        boolean suporte = texto.matches(".*\\b(problema|erro|defeito|suporte|falha|quebrado)\\b.*");
        boolean comercial = texto.matches(".*\\b(orcamento|preco|valor|comprar|contratar|proposta|comercial)\\b.*");
        if (suporte != comercial) {
            conversa.definirIntencao(suporte ? Conversa.Intencao.SUPORTE : Conversa.Intencao.COMERCIAL);
            return Optional.empty();
        }
        // Dúvidas gerais não precisam passar por um menu antes da busca documental.
        if (!suporte) return Optional.empty();
        return preparar(conversa, mensagem, "Para direcionar seu atendimento, envie /suporte para ajuda técnica ou /comercial para informações e propostas. Você também pode pedir /atendente.");
    }
    private Optional<String> preparar(Conversa conversa, MensagemTelegram mensagem, String texto) {
        long id = conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0) + 1;
        conversa.adicionarMensagem(id, texto, Mensagem.TipoRemetente.CHATBOT);
        fila.preparar(conversa.getEmpresa().getId(), mensagem.atualizacaoId(), mensagem.chatId(), texto);
        return Optional.of(texto);
    }
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
