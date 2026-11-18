package com.wxn.chatbot.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Referencias de uma resposta gerada com trechos oficiais (HU02.2).
 *
 * Contrato com a geracao: cada trecho entregue a IA recebe um rotulo
 * {@code F1}, {@code F2}... na ordem de {@link #numerar}, e a IA cita o trecho
 * escrevendo o rotulo entre colchetes, por exemplo {@code [F1]} ou {@code [F1, F3]}.
 *
 * {@link #extrair} aceita somente rotulos que correspondem a trechos realmente
 * fornecidos; rotulos inventados sao removidos e contados. No texto final as
 * marcas viram {@code [1]}, {@code [2]}... na ordem da primeira citacao, que e a
 * mesma numeracao mostrada no painel e na lista de fontes do Telegram.
 */
public final class ReferenciasResposta {
    private static final Pattern GRUPO = Pattern.compile(
            " ?\\[\\s*(F\\s*\\d+(?:\\s*,\\s*F\\s*\\d+)*)\\s*\\]", Pattern.CASE_INSENSITIVE);

    private ReferenciasResposta() {
    }

    /** Fonte citada com o numero que aparece no texto final. */
    public record Citacao(int numero, FonteDisponivel fonte) {
    }

    /** Texto pronto para salvar, fontes citadas validas e quantas referencias foram descartadas. */
    public record Resultado(String texto, List<Citacao> citadas, int referenciasInvalidas) {
        public Resultado {
            citadas = List.copyOf(citadas);
        }
    }

    /** Remove trechos repetidos mantendo a ordem; o indice + 1 e o numero do rotulo F. */
    public static List<FonteDisponivel> numerar(List<FonteDisponivel> fornecidas) {
        Map<String, FonteDisponivel> unicas = new LinkedHashMap<>();
        if (fornecidas != null) {
            for (FonteDisponivel fonte : fornecidas) {
                if (fonte != null) unicas.putIfAbsent(fonte.chave(), fonte);
            }
        }
        return List.copyOf(unicas.values());
    }

    /** Rotulo usado no prompt para o trecho na posicao informada (comecando em 0). */
    public static String rotulo(int posicao) {
        if (posicao < 0) throw new IllegalArgumentException("A posicao deve ser zero ou maior.");
        return "F" + (posicao + 1);
    }

    public static Resultado extrair(String resposta, List<FonteDisponivel> fornecidas) {
        if (resposta == null || resposta.isBlank()) {
            throw new IllegalArgumentException("A resposta nao pode estar vazia.");
        }
        List<FonteDisponivel> numeradas = numerar(fornecidas);
        Map<Integer, Integer> numeroPorRotulo = new LinkedHashMap<>();
        List<Citacao> citadas = new ArrayList<>();
        int invalidas = 0;

        Matcher grupo = GRUPO.matcher(resposta);
        StringBuilder texto = new StringBuilder();
        while (grupo.find()) {
            List<Integer> validos = new ArrayList<>();
            for (String parte : grupo.group(1).split(",")) {
                int rotulo = Integer.parseInt(parte.replaceAll("(?i)[\\sF]", ""));
                if (rotulo < 1 || rotulo > numeradas.size()) {
                    invalidas++;
                    continue;
                }
                Integer numero = numeroPorRotulo.get(rotulo);
                if (numero == null) {
                    numero = numeroPorRotulo.size() + 1;
                    numeroPorRotulo.put(rotulo, numero);
                    citadas.add(new Citacao(numero, numeradas.get(rotulo - 1)));
                }
                if (!validos.contains(numero)) validos.add(numero);
            }
            String substituto = validos.isEmpty() ? ""
                    : (grupo.group().startsWith(" ") ? " " : "") + "[" + juntar(validos) + "]";
            grupo.appendReplacement(texto, Matcher.quoteReplacement(substituto));
        }
        grupo.appendTail(texto);

        String limpo = texto.toString().replaceAll("[ \\t]+([.,;:!?])", "$1").strip();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("A resposta ficou vazia depois de remover as referencias.");
        }
        return new Resultado(limpo, citadas, invalidas);
    }

    private static String juntar(List<Integer> numeros) {
        return String.join(", ", numeros.stream().map(numero -> String.format(Locale.ROOT, "%d", numero)).toList());
    }
}
