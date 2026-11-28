package com.wxn.chatbot.telegram;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Organiza os botões e traduz pedidos explícitos para os comandos já existentes. */
final class NavegacaoTelegram {
    static final List<List<Opcao>> MENU = List.of(
            List.of(new Opcao("Suporte", "/suporte"), new Opcao("Comercial", "/comercial")),
            List.of(new Opcao("Abrir chamado", "/chamado"), new Opcao("Falar com atendente", "/atendente")),
            List.of(new Opcao("Pedir orçamento", "/orcamento"), new Opcao("Pedir demonstração", "/demonstracao")),
            List.of(new Opcao("Pedir reunião", "/reuniao"), new Opcao("Pedir visita técnica", "/visita")),
            List.of(new Opcao("Minhas solicitações", "/solicitacoes"), new Opcao("Meus planos", "/planos")),
            List.of(new Opcao("Confirmar identidade", "/validar"), new Opcao("Encerrar conversa", "/encerrar")),
            List.of(new Opcao("Cancelar pedido", "/cancelar")));

    static final String APRESENTACAO = """
            Como posso ajudar?

            • Suporte e comercial: tire dúvidas, abra um chamado ou fale com um atendente.
            • Pedidos: solicite orçamento, demonstração, reunião ou visita técnica.
            • Consultas: veja suas solicitações com o cadastro do Telegram. A consulta de planos exige confirmação de identidade.

            Escolha um botão ou escreva o que você precisa.
            """.strip();

    private static final Map<String, String> PEDIDOS = Map.ofEntries(
            Map.entry("abrir um chamado", "/chamado"), Map.entry("abrir chamado de suporte", "/chamado"),
            Map.entry("pedir um orcamento", "/orcamento"), Map.entry("solicitar orcamento", "/orcamento"),
            Map.entry("falar com um atendente", "/atendente"), Map.entry("falar com uma pessoa", "/atendente"),
            Map.entry("ver minhas solicitacoes", "/solicitacoes"), Map.entry("consultar minhas solicitacoes", "/solicitacoes"),
            Map.entry("ver meus planos", "/planos"), Map.entry("consultar meu plano", "/planos"),
            Map.entry("solicitar uma demonstracao", "/demonstracao"), Map.entry("agendar uma reuniao", "/reuniao"),
            Map.entry("solicitar uma visita tecnica", "/visita"), Map.entry("cancelar", "/cancelar"),
            Map.entry("menu", "/menu"), Map.entry("ajuda", "/menu"), Map.entry("/ajuda", "/menu"));

    private static final Pattern CAPACIDADES = Pattern.compile(
            "(?:o ?q(?:ue)?|que) (?:(?:voce|vc|vce) )?pode+ fazer(?: por mim)?|como (?:voce |vc )?pode me ajudar");

    private NavegacaoTelegram() { }

    record Opcao(String rotulo, String comando) { }

    static String comando(String entrada) {
        String frase = normalizar(entrada);
        if (CAPACIDADES.matcher(frase).matches()) return "/menu";
        for (var linha : MENU) {
            for (var opcao : linha) {
                if (normalizar(opcao.rotulo()).equals(frase)) return opcao.comando();
            }
        }
        String pedido = frase.replaceFirst("^(?:eu )?(?:quero|gostaria de|preciso) ", "");
        return PEDIDOS.getOrDefault(pedido, entrada);
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[?!.,]+$", "").replaceAll("\\s+", " ").strip();
    }
}
