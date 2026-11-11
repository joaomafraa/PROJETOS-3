package com.wxn.chatbot.llm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroqProvedor implements ProvedorLlm {
    private final OpenAiChatModel modelo;
    private final String chave;
    public GroqProvedor(OpenAiChatModel modelo, @Value("${GROQ_API_KEY:}") String chave) {
        this.modelo = modelo;
        this.chave = chave;
    }
    @Override
    public String responder(List<TrechoConversa> historico) {
        if (chave == null || chave.isBlank()) throw new FalhaLlmException("Configure GROQ_API_KEY no ambiente.");
        List<Message> mensagens = new ArrayList<>();
        mensagens.add(new SystemMessage("Você é um assistente de atendimento. Não invente informações sobre a empresa; quando não souber, informe que não possui essa informação."));
        for (TrechoConversa trecho : historico) {
            if ("CHATBOT".equals(trecho.autor())) mensagens.add(new AssistantMessage(trecho.conteudo()));
            else mensagens.add(new UserMessage(trecho.conteudo()));
        }
        try {
            var resposta = modelo.call(new Prompt(mensagens));
            if (resposta == null || resposta.getResult() == null || resposta.getResult().getOutput() == null
                    || resposta.getResult().getOutput().getText() == null || resposta.getResult().getOutput().getText().isBlank())
                throw new FalhaLlmException("A Groq não retornou uma resposta de texto.");
            return resposta.getResult().getOutput().getText().strip();
        } catch (FalhaLlmException erro) { throw erro; }
        catch (RuntimeException erro) { throw new FalhaLlmException("Não foi possível obter resposta da Groq. Verifique a configuração e tente novamente."); }
    }
}
