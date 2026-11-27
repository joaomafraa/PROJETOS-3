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

    /** Teclado opcional enviado junto da resposta. */
    public enum Teclado {
        NENHUM,
        /** Botão que envia o contato da própria conta (request_contact). */
        PEDIR_TELEFONE,
        /** Menu clicável com comandos e opção de compartilhar contato. */
        MENU,
        /** Retira o botão anterior da tela do usuário. */
        REMOVER
    }

    static final String BOTAO_TELEFONE = "Compartilhar meu telefone";

    public void enviarMensagem(long chatId, String texto) {
        enviarMensagem(chatId, texto, Teclado.NENHUM);
    }

    public void enviarMensagem(long chatId, String texto, Teclado teclado) {
        if (chatId == 0 || texto == null || texto.isBlank() || teclado == null) throw new IllegalArgumentException("Chat e mensagem são obrigatórios.");
        Map<String, Object> corpo = new java.util.LinkedHashMap<>();
        corpo.put("chat_id", chatId);
        corpo.put("text", texto);
        if (teclado == Teclado.PEDIR_TELEFONE) {
            corpo.put("reply_markup", Map.of(
                    "keyboard", List.of(List.of(Map.of("text", BOTAO_TELEFONE, "request_contact", true))),
                    "resize_keyboard", true, "one_time_keyboard", true));
        } else if (teclado == Teclado.MENU) {
            corpo.put("reply_markup", Map.of("keyboard", List.of(
                    List.of(Map.of("text", "/suporte"), Map.of("text", "/comercial")),
                    List.of(Map.of("text", "/atendente"), Map.of("text", "/chamado")),
                    List.of(Map.of("text", "/orcamento"), Map.of("text", "/demonstracao"), Map.of("text", "/reuniao"), Map.of("text", "/visita")),
                    List.of(Map.of("text", "/solicitacoes"), Map.of("text", "/planos")),
                    List.of(Map.of("text", "/validar"), Map.of("text", BOTAO_TELEFONE, "request_contact", true))),
                    "resize_keyboard", true, "one_time_keyboard", false));
        } else if (teclado == Teclado.REMOVER) {
            corpo.put("reply_markup", Map.of("remove_keyboard", true));
        }
        executar("sendMessage", corpo);
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
