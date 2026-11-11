package com.wxn.chatbot.telegram;
import java.util.Map;
import java.util.Optional;
public record MensagemTelegram(long atualizacaoId, long chatId, long usuarioId, String nome, String texto) {
    public static Optional<MensagemTelegram> interpretar(Object entrada) {
        if (!(entrada instanceof Map<?, ?> update) || !(update.get("update_id") instanceof Number id)
            || !(update.get("message") instanceof Map<?, ?> mensagem) || !(mensagem.get("chat") instanceof Map<?, ?> chat)
            || !"private".equals(chat.get("type")) || !(chat.get("id") instanceof Number chatId)
            || !(mensagem.get("from") instanceof Map<?, ?> usuario) || Boolean.TRUE.equals(usuario.get("is_bot"))
            || !(usuario.get("id") instanceof Number usuarioId) || !(mensagem.get("text") instanceof String texto)
            || texto.isBlank() || id.longValue() < 0 || chatId.longValue() <= 0 || chatId.longValue() != usuarioId.longValue()) return Optional.empty();
        String nome = usuario.get("first_name") instanceof String n ? n.strip() : "";
        return Optional.of(new MensagemTelegram(id.longValue(), chatId.longValue(), usuarioId.longValue(), nome, texto.strip()));
    }
}
