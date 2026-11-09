package com.wxn.chatbot.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class GeminiProvedor implements ProvedorLlm {
    private final String chave;
    private final String modelo;
    private final RestClient cliente;

    public GeminiProvedor(@Value("${GEMINI_API_KEY:}") String chave,
            @Value("${GEMINI_MODEL:gemini-2.5-flash-lite}") String modelo) {
        this.chave = chave;
        this.modelo = modelo == null || modelo.isBlank() ? "gemini-2.5-flash-lite" : modelo;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String responder(List<TrechoConversa> historico) {
        if (chave == null || chave.isBlank()) throw new FalhaLlmException("Configure GEMINI_API_KEY no ambiente.");
        if (!modelo.matches("[A-Za-z0-9._-]+")) throw new FalhaLlmException("Modelo Gemini inválido.");
        var texto = new StringBuilder("Você é um assistente de atendimento. Não invente informações sobre a empresa; quando não souber, informe que não possui essa informação.\nHistórico:\n");
        historico.forEach(item -> texto.append(item.autor()).append(": ").append(item.conteudo()).append('\n'));
        try {
            Map<?, ?> resposta = cliente.post()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent"))
                    .header("x-goog-api-key", chave)
                    .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", texto.toString()))))))
                    .retrieve().body(Map.class);
            if (resposta != null && resposta.get("candidates") instanceof List<?> candidatos && !candidatos.isEmpty()
                    && candidatos.get(0) instanceof Map<?, ?> candidato
                    && candidato.get("content") instanceof Map<?, ?> conteudo
                    && conteudo.get("parts") instanceof List<?> partes) {
                StringBuilder saida = new StringBuilder();
                for (Object parte : partes) {
                    if (parte instanceof Map<?, ?> mapa && mapa.get("text") instanceof String trecho) saida.append(trecho);
                }
                if (!saida.toString().isBlank()) return saida.toString().strip();
            }
            throw new FalhaLlmException("O Gemini não retornou uma resposta de texto.");
        } catch (HttpClientErrorException.TooManyRequests erro) {
            throw new FalhaLlmException("Limite de uso do Gemini atingido. Tente novamente mais tarde.");
        } catch (RestClientException erro) {
            throw new FalhaLlmException("Não foi possível obter resposta do Gemini. Verifique a configuração e tente novamente.");
        }
    }
}
