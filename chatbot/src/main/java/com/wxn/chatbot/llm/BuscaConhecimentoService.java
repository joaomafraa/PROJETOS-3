package com.wxn.chatbot.llm;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wxn.chatbot.service.DocumentosConhecimentoService;
import com.wxn.chatbot.service.DocumentosConhecimentoService.Documento;

@Service
public class BuscaConhecimentoService {

    public static final int MAXIMO_TRECHOS = 5;
    public static final int MAXIMO_CARACTERES = 4000;
    static final double COBERTURA_MINIMA = 0.4;
    static final double FRACAO_DO_MELHOR = 0.3;
    private static final int TAMANHO_MAXIMO_PERGUNTA = 1000;

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private static final Set<String> PALAVRAS_COMUNS = Set.copyOf(List.of(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "por", "pelo", "pela", "pelos", "pelas", "para", "pra", "pro", "com", "sem", "ao", "aos",
            "e", "ou", "mas", "que", "se", "como", "qual", "quais", "quando", "onde", "quanto", "quanta", "quantos",
            "quantas", "porque", "eu", "voce", "voces", "vc", "vcs", "ele", "ela", "eles", "elas", "nos",
            "me", "te", "lhe", "meu", "minha", "meus", "minhas", "seu", "sua", "seus", "suas", "este", "esta",
            "esse", "essa", "isso", "isto", "aquele", "aquela", "aqui", "ai", "la", "ja", "ainda", "tambem",
            "entao", "sobre", "ate", "mais", "muito", "muita", "ser", "sao", "e", "foi", "era", "esta", "estao",
            "tem", "ter", "ha", "pode", "posso", "podem", "poderia", "gostaria", "queria", "quero", "saber",
            "preciso", "favor", "oi", "ola", "bom", "boa", "obrigado", "obrigada", "tudo", "bem", "nao", "sim",
            "algum", "alguma", "qualquer", "fazer", "faz", "sei", "tipo", "coisa"));

    private final DocumentosConhecimentoService documentos;

    public BuscaConhecimentoService(DocumentosConhecimentoService documentos) {
        this.documentos = documentos;
    }

    public ResultadoBusca buscar(Long empresaId, String pergunta) {
        if (empresaId == null || empresaId <= 0) throw new IllegalArgumentException("O id da empresa deve ser positivo.");
        String texto = pergunta == null ? "" : pergunta.strip();
        if (texto.length() > TAMANHO_MAXIMO_PERGUNTA) texto = texto.substring(0, TAMANHO_MAXIMO_PERGUNTA);
        List<String> termos = List.copyOf(new LinkedHashSet<>(termos(texto)));
        if (termos.isEmpty()) return ResultadoBusca.vazio(ResultadoBusca.Situacao.PERGUNTA_SEM_TERMOS);

        List<Documento> ativos = documentos.ativosDaEmpresa(empresaId);
        if (ativos.isEmpty()) return ResultadoBusca.vazio(ResultadoBusca.Situacao.SEM_DOCUMENTOS);

        List<Candidato> candidatos = new ArrayList<>();
        for (Documento documento : ativos) {
            if (!documento.ativo()) continue; // defesa extra: o servico ja filtra
            for (DivisorTrechos.Trecho trecho : DivisorTrechos.dividir(documento.conteudo())) {
                String pesquisavel = documento.titulo() + " " + (trecho.localizacao() == null ? "" : trecho.localizacao())
                        + " " + trecho.texto();
                candidatos.add(new Candidato(empresaId, documento, trecho, termos(pesquisavel)));
            }
        }
        if (candidatos.isEmpty()) return ResultadoBusca.vazio(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA);

        pontuar(candidatos, termos);
        int coberturaMinima = Math.max(1, (int) Math.ceil(termos.size() * COBERTURA_MINIMA));
        double melhor = candidatos.stream().filter(c -> c.termosEncontrados >= coberturaMinima)
                .mapToDouble(c -> c.pontuacao).max().orElse(0);
        if (melhor <= 0) return ResultadoBusca.vazio(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA);

        List<Candidato> aceitos = candidatos.stream()
                .filter(c -> c.termosEncontrados >= coberturaMinima && c.pontuacao >= melhor * FRACAO_DO_MELHOR)
                .sorted(Comparator.comparingDouble((Candidato c) -> c.pontuacao).reversed()
                        .thenComparing(c -> c.documento.id())
                        .thenComparingInt(c -> c.ordem()))
                .toList();

        List<ResultadoBusca.TrechoEncontrado> escolhidos = new ArrayList<>();
        int caracteres = 0;
        for (Candidato candidato : aceitos) {
            if (escolhidos.size() == MAXIMO_TRECHOS) break;
            int tamanho = candidato.trecho.texto().length();
            if (caracteres + tamanho > MAXIMO_CARACTERES) continue;
            caracteres += tamanho;
            escolhidos.add(new ResultadoBusca.TrechoEncontrado(candidato.fonte(), candidato.pontuacao));
        }
        return new ResultadoBusca(ResultadoBusca.Situacao.ENCONTRADO, escolhidos);
    }

    // BM25: termos raros pesam mais; repeticao ajuda com retorno decrescente; trechos longos sao normalizados.
    private static void pontuar(List<Candidato> candidatos, List<String> termos) {
        int total = candidatos.size();
        double tamanhoMedio = candidatos.stream().mapToInt(c -> c.termos.size()).average().orElse(1);
        Map<String, Integer> frequenciaDocumentos = new HashMap<>();
        for (String termo : termos) {
            frequenciaDocumentos.put(termo, (int) candidatos.stream().filter(c -> c.contagem(termo) > 0).count());
        }
        for (Candidato candidato : candidatos) {
            double pontuacao = 0;
            int encontrados = 0;
            for (String termo : termos) {
                int ocorrencias = candidato.contagem(termo);
                if (ocorrencias == 0) continue;
                encontrados++;
                int df = frequenciaDocumentos.get(termo);
                double idf = Math.log(1 + (total - df + 0.5) / (df + 0.5));
                double normalizacao = K1 * (1 - B + B * candidato.termos.size() / tamanhoMedio);
                pontuacao += idf * ocorrencias * (K1 + 1) / (ocorrencias + normalizacao);
            }
            candidato.pontuacao = pontuacao;
            candidato.termosEncontrados = encontrados;
        }
    }

    /** Termos pesquisaveis: minusculos, sem acento, sem palavras comuns e reduzidos ao radical simples. */
    static List<String> termos(String texto) {
        List<String> termos = new ArrayList<>();
        if (texto == null || texto.isBlank()) return termos;
        String normalizado = Normalizer.normalize(texto.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (String palavra : normalizado.split("[^\\p{L}\\p{N}]+")) {
            if (palavra.isEmpty() || PALAVRAS_COMUNS.contains(palavra)) continue;
            if (palavra.length() < 2 && !palavra.chars().allMatch(Character::isDigit)) continue;
            termos.add(radical(palavra));
        }
        return termos;
    }

    // Reducao leve de plural para portugues, suficiente para "prazos" ~ "prazo", "informacoes" ~ "informacao".
    static String radical(String palavra) {
        if (palavra.length() <= 3 || palavra.chars().allMatch(Character::isDigit)) return palavra;
        if (palavra.endsWith("coes")) return palavra.substring(0, palavra.length() - 4) + "cao";
        if (palavra.endsWith("oes") || palavra.endsWith("aes")) return palavra.substring(0, palavra.length() - 3) + "ao";
        if (palavra.endsWith("ais")) return palavra.substring(0, palavra.length() - 3) + "al";
        if (palavra.endsWith("eis")) return palavra.substring(0, palavra.length() - 3) + "el";
        if (palavra.endsWith("ns")) return palavra.substring(0, palavra.length() - 2) + "m";
        if (palavra.matches(".*[rzs]es") && palavra.length() > 5) return palavra.substring(0, palavra.length() - 2);
        if (palavra.endsWith("s") && !palavra.endsWith("ss")) return palavra.substring(0, palavra.length() - 1);
        return palavra;
    }

    // Termos equivalentes: mesmo radical, ou um e prefixo do outro com pelo menos 5 letras ("entrega" ~ "entregar").
    static boolean equivalentes(String termoPergunta, String termoTrecho) {
        if (termoPergunta.equals(termoTrecho)) return true;
        if (termoPergunta.length() < 5 || termoTrecho.length() < 5) return false;
        return termoTrecho.startsWith(termoPergunta) || termoPergunta.startsWith(termoTrecho);
    }

    private static final class Candidato {
        private final Long empresaId;
        private final Documento documento;
        private final DivisorTrechos.Trecho trecho;
        private final List<String> termos;
        private final Map<String, Integer> contagens = new HashMap<>();
        private double pontuacao;
        private int termosEncontrados;

        Candidato(Long empresaId, Documento documento, DivisorTrechos.Trecho trecho, List<String> termos) {
            this.empresaId = empresaId;
            this.documento = documento;
            this.trecho = trecho;
            this.termos = termos;
        }

        int contagem(String termoPergunta) {
            return contagens.computeIfAbsent(termoPergunta,
                    t -> (int) termos.stream().filter(termo -> equivalentes(t, termo)).count());
        }

        int ordem() {
            return Integer.parseInt(trecho.id().substring(1));
        }

        FonteDisponivel fonte() {
            return new FonteDisponivel(empresaId, documento.id(), Integer.toString(documento.versao()),
                    documento.titulo(), trecho.id(), trecho.localizacao(), trecho.texto());
        }
    }
}
