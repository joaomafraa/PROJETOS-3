package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SegurancaContextoConhecimentoTests {

    private final SegurancaContextoConhecimento seguranca = new SegurancaContextoConhecimento();

    private static final FonteDisponivel ATIVA = fonte(1L, 10L, "v2", "t-1", "conteudo ativo");
    private static final FonteDisponivel INATIVA = fonte(1L, 11L, "v1", "t-2", "conteudo inativo");
    private static final FonteDisponivel EXTERNA = fonte(2L, 90L, "v1", "t-1", "segredo externo");

    @Test
    void provedorRecebeSomenteTrechosAtivosDaEmpresaCorreta() {
        Set<String> disponiveis = Set.of(chave(1L, 10L, "v2", "t-1"));
        var contexto = seguranca.preparar(1L, List.of(ATIVA, INATIVA, ATIVA),
                (empresa, documento, versao, trecho) ->
                        disponiveis.contains(chave(empresa, documento, versao, trecho)));

        ProvedorCaptura provedor = new ProvedorCaptura();
        provedor.responder(contexto);

        assertEquals(List.of(ATIVA), provedor.contextoRecebido);
        assertTrue(provedor.contextoRecebido.stream()
                .noneMatch(fonte -> fonte.texto().contains("inativo") || fonte.texto().contains("externo")));
    }

    @Test
    void fonteDeOutraEmpresaInterrompeAMontagemAntesDoProvedor() {
        AtomicInteger consultas = new AtomicInteger();

        assertThrows(SecurityException.class, () -> seguranca.preparar(1L, List.of(ATIVA, EXTERNA),
                (empresa, documento, versao, trecho) -> {
                    consultas.incrementAndGet();
                    return true;
                }));

        assertEquals(0, consultas.get(), "Nenhum trecho deve chegar a etapa de montagem quando o tenant diverge.");
    }

    @Test
    void versaoAntigaOuTrechoRemovidoNaoEntraNoContexto() {
        var versaoAntiga = fonte(1L, 10L, "v1", "t-1", "texto antigo");
        var trechoRemovido = fonte(1L, 10L, "v2", "t-9", "texto removido");

        var contexto = seguranca.preparar(1L, List.of(versaoAntiga, trechoRemovido, ATIVA),
                (empresa, documento, versao, trecho) ->
                        chave(empresa, documento, versao, trecho).equals(chave(1L, 10L, "v2", "t-1")));

        assertEquals(List.of(ATIVA), contexto.fontes());
    }

    @Test
    void desativacaoDuranteGeracaoFazRespostaSerDescartada() {
        Set<String> disponiveis = new HashSet<>();
        disponiveis.add(chave(1L, 10L, "v2", "t-1"));
        DisponibilidadeFonteConhecimento consulta = (empresa, documento, versao, trecho) ->
                disponiveis.contains(chave(empresa, documento, versao, trecho));

        var contexto = seguranca.preparar(1L, List.of(ATIVA), consulta);
        disponiveis.clear(); // documento desativado enquanto o provedor gerava a resposta

        assertThrows(ContextoConhecimentoObsoletoException.class,
                () -> seguranca.revalidar(contexto, consulta));
    }

    @Test
    void contextoEhImutavelENaoAceitaFonteNula() {
        List<FonteDisponivel> mutavel = new ArrayList<>(List.of(ATIVA));
        var contexto = seguranca.preparar(1L, mutavel, (empresa, documento, versao, trecho) -> true);
        mutavel.add(INATIVA);

        assertEquals(List.of(ATIVA), contexto.fontes());
        assertThrows(UnsupportedOperationException.class, () -> contexto.fontes().add(INATIVA));
        assertThrows(IllegalArgumentException.class,
                () -> seguranca.validarPertencimento(1L, java.util.Arrays.asList(ATIVA, null)));
    }

    private static FonteDisponivel fonte(Long empresa, Long documento, String versao, String trecho, String texto) {
        return new FonteDisponivel(empresa, documento, versao, "Documento " + documento, trecho, null, texto);
    }

    private static String chave(Long empresa, Long documento, String versao, String trecho) {
        return empresa + "|" + documento + "|" + versao + "|" + trecho;
    }

    /** Substituto do provedor que registra exatamente os trechos enviados ao prompt. */
    private static final class ProvedorCaptura {
        private List<FonteDisponivel> contextoRecebido = List.of();

        void responder(ContextoConhecimentoSeguro contexto) {
            contextoRecebido = List.copyOf(contexto.fontes());
        }
    }
}
