package com.wxn.chatbot.telegram;
import java.util.Map;
import java.util.Optional;

public record MensagemTelegram(long atualizacaoId, long chatId, long usuarioId, String nome, String texto, String telefone) {
    static final String TEXTO_CONTATO = "[contato compartilhado]";

    public MensagemTelegram(long atualizacaoId, long chatId, long usuarioId, String nome, String texto) {
        this(atualizacaoId, chatId, usuarioId, nome, texto, null);
    }

    public boolean compartilhouContato() {
        return TEXTO_CONTATO.equals(texto);
    }

    public static Optional<MensagemTelegram> interpretar(Object entrada) {
        if (!(entrada instanceof Map<?, ?> update) || !(update.get("update_id") instanceof Number id)
            || !(update.get("message") instanceof Map<?, ?> mensagem) || !(mensagem.get("chat") instanceof Map<?, ?> chat)
            || !"private".equals(chat.get("type")) || !(chat.get("id") instanceof Number chatId)
            || !(mensagem.get("from") instanceof Map<?, ?> usuario) || Boolean.TRUE.equals(usuario.get("is_bot"))
            || !(usuario.get("id") instanceof Number usuarioId)
            || id.longValue() < 0 || chatId.longValue() <= 0 || chatId.longValue() != usuarioId.longValue()) return Optional.empty();
        String nome = usuario.get("first_name") instanceof String n ? n.strip() : "";
        if (mensagem.get("contact") instanceof Map<?, ?> contato) {
            String telefone = null;
            if (contato.get("user_id") instanceof Number dono && dono.longValue() == usuarioId.longValue()
                    && contato.get("phone_number") instanceof String numero) {
                telefone = formatoInternacional(numero);
            }
            return Optional.of(new MensagemTelegram(id.longValue(), chatId.longValue(), usuarioId.longValue(), nome, TEXTO_CONTATO, telefone));
        }
        if (!(mensagem.get("text") instanceof String texto) || texto.isBlank()) return Optional.empty();
        return Optional.of(new MensagemTelegram(id.longValue(), chatId.longValue(), usuarioId.longValue(), nome, texto.strip()));
    }

    // O Telegram envia o numero da conta no formato internacional, as vezes sem o "+".
    private static String formatoInternacional(String numero) {
        String limpo = numero.strip().replaceAll("[ ()-]", "");
        if (limpo.matches("[0-9]{8,15}")) return "+" + limpo;
        return limpo.matches("\\+[0-9]{8,15}") ? limpo : null;
    }
}
