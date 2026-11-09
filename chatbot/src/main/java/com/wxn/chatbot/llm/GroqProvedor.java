package com.wxn.chatbot.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GroqProvedor implements ProvedorLlm {
    private final String chave;
    private final String modelo;
    private final RestClient cliente;

    @org.springframework.beans.factory.annotation.Autowired
    public GroqProvedor(@Value("${GROQ_API_KEY:}") String chave,
            @Value("${GROQ_MODEL:openai/gpt-oss-120b}") String modelo) {
        this(chave, modelo, criarCliente());
    }

    GroqProvedor(String chave, String modelo, RestClient cliente) {
        this.chave = chave;
        this.modelo = modelo == null || modelo.isBlank() ? "openai/gpt-oss-120b" : modelo;
        this.cliente = cliente;
    }

    private static RestClient criarCliente() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(fabrica).baseUrl("https://api.groq.com/openai/v1").build();
    }

    @Override
    public String responder(List<TrechoConversa> historico) {
        if (chave == null || chave.isBlank()) throw new FalhaLlmException("Configure GROQ_API_KEY no ambiente.");
        var mensagens = new ArrayList<Map<String, String>>();
        mensagens.add(Map.of("role", "system", "content", "Você é um assistente de atendimento. Não invente informações sobre a empresa; quando não souber, informe que não possui essa informação."));
        for (TrechoConversa trecho : historico) {
            String role = "CHATBOT".equals(trecho.autor()) ? "assistant" : "user";
            mensagens.add(Map.of("role", role, "content", trecho.conteudo()));
        }
        try {
            Map<?, ?> resposta = cliente.post().uri("/chat/completions")
                    .headers(h -> h.setBearerAuth(chave))
                    .body(Map.of("model", modelo, "messages", mensagens))
                    .retrieve().body(Map.class);
            if (resposta != null && resposta.get("choices") instanceof List<?> escolhas && !escolhas.isEmpty()
                    && escolhas.get(0) instanceof Map<?, ?> escolha
                    && escolha.get("message") instanceof Map<?, ?> mensagem
                    && mensagem.get("content") instanceof String texto && !texto.isBlank()) {
                return texto.strip();
            }
            throw new FalhaLlmException("A Groq não retornou uma resposta de texto.");
        } catch (HttpClientErrorException.TooManyRequests erro) {
            throw new FalhaLlmException("Limite de uso da Groq atingido. Tente novamente mais tarde.");
        } catch (RestClientException erro) {
            throw new FalhaLlmException("Não foi possível obter resposta da Groq. Verifique a configuração e tente novamente.");
        }
    }
}
