package com.wxn.chatbot.telegram;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cliente de transporte. O token nunca deve ser exibido em erros ou logs. */
@Component
public class TelegramCliente {
    private final String token;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramCliente(@Value("${TELEGRAM_BOT_TOKEN:}") String token) {
        this(token, criarCliente());
    }

    TelegramCliente(String token, RestClient client) {
        this.token = token == null ? "" : token.strip();
        this.client = client;
    }

    private static RestClient criarCliente() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api.telegram.org").requestFactory(factory).build();
    }

    public String verificarBot() {
        Map<?, ?> resposta = executar("getMe", Map.of());
        if (resposta.get("result") instanceof Map<?, ?> bot && bot.get("username") instanceof String nome) return nome;
        throw new IllegalStateException("O Telegram retornou dados inválidos do bot.");
    }

    public void enviarMensagem(long chatId, String texto) {
        if (chatId == 0 || texto == null || texto.isBlank()) throw new IllegalArgumentException("Chat e mensagem são obrigatórios.");
        executar("sendMessage", Map.of("chat_id", chatId, "text", texto));
    }

    /** Consulta atualizações sem confirmá-las; o chamador controla o offset após persistência. */
    public List<?> buscarAtualizacoes(long offset) {
        if (offset < 0) throw new IllegalArgumentException("O offset não pode ser negativo.");
        Map<?, ?> resposta = executar("getUpdates", Map.of("offset", offset, "timeout", 0,
                "allowed_updates", List.of("message")));
        if (resposta.get("result") instanceof List<?> atualizacoes) return List.copyOf(atualizacoes);
        throw new IllegalStateException("O Telegram retornou atualizações inválidas.");
    }

    private Map<?, ?> executar(String metodo, Map<String, ?> corpo) {
        if (token.isBlank()) throw new IllegalStateException("Configure TELEGRAM_BOT_TOKEN no ambiente.");
        try {
            Map<?, ?> resposta = client.post().uri("/bot" + token + "/" + metodo).body(corpo).retrieve().body(Map.class);
            if (resposta == null || !Boolean.TRUE.equals(resposta.get("ok"))) throw new IllegalStateException("O Telegram recusou a solicitação.");
            return resposta;
        } catch (RestClientException erro) {
            throw new IllegalStateException("Não foi possível comunicar com o Telegram. Verifique a configuração e tente novamente.");
        }
    }
}
