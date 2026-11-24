package com.wxn.chatbot.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wxn.chatbot.service.DocumentosConhecimentoService;
import com.wxn.chatbot.service.DocumentosConhecimentoService.Documento;

class BuscaConhecimentoServiceTests {

    private static final long EMPRESA = 7L;

    private static final Documento ENTREGAS = new Documento(10L, "Política de entregas", """
            # Prazos de entrega
            O prazo de entrega padrão é de 5 dias úteis após a confirmação do pagamento.

            Para Recife e região metropolitana, a entrega expressa leva 2 dias úteis.

            # Trocas e devoluções
            O cliente pode solicitar a troca em até 7 dias corridos após o recebimento.

            # Formas de pagamento
            Aceitamos Pix, boleto e cartão de crédito em até 3 vezes sem juros.
            """, 3, true);

    private static final Documento SUPORTE = new Documento(11L, "Suporte técnico", """
            # Horário do suporte
            O suporte técnico funciona de segunda a sexta, das 8h às 18h.

            # Acesso ao portal
            Para redefinir a senha do portal do cliente, use a opção Esqueci minha senha na tela de login.
            """, 1, true);

    private DocumentosConhecimentoService documentos;
    private BuscaConhecimentoService busca;

    @BeforeEach
    void preparar() {
        documentos = mock(DocumentosConhecimentoService.class);
        when(documentos.ativosDaEmpresa(EMPRESA)).thenReturn(List.of(ENTREGAS, SUPORTE));
        busca = new BuscaConhecimentoService(documentos);
    }

    @Test
    void encontraOTrechoDaPerguntaPrimeiroComAIdentificacaoDaFonte() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "Qual o prazo de entrega?");

        assertEquals(ResultadoBusca.Situacao.ENCONTRADO, resultado.situacao());
        FonteDisponivel primeira = resultado.fontes().get(0);
        assertEquals(EMPRESA, primeira.empresaId());
        assertEquals(10L, primeira.documentoId());
        assertEquals("3", primeira.documentoVersao());
        assertEquals("Política de entregas", primeira.documentoTitulo());
        assertEquals("t1", primeira.trechoId());
        assertEquals("Seção: Prazos de entrega", primeira.localizacao());
        assertTrue(primeira.texto().contains("5 dias úteis"));
        assertTrue(resultado.fontes().stream().noneMatch(f -> f.documentoId().equals(11L)),
                "O documento de suporte não fala de entrega.");
        verify(documentos).ativosDaEmpresa(EMPRESA);
    }

    @Test
    void ordenaDoMaisParaOMenosRelevante() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "Qual o prazo de entrega?");
        List<Double> relevancias = resultado.trechos().stream().map(ResultadoBusca.TrechoEncontrado::relevancia).toList();
        for (int i = 1; i < relevancias.size(); i++) {
            assertTrue(relevancias.get(i - 1) >= relevancias.get(i));
        }
    }

    @Test
    void ignoraAcentoMaiusculaEPlural() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "PRAZOS DE ENTREGAS");
        assertTrue(resultado.encontrou());
        assertEquals("t1", resultado.fontes().get(0).trechoId());

        ResultadoBusca senha = busca.buscar(EMPRESA, "como faço pra redefinir minha senha do portal?");
        assertEquals(11L, senha.fontes().get(0).documentoId());
        assertEquals("Seção: Acesso ao portal", senha.fontes().get(0).localizacao());
    }

    @Test
    void verboEncontraSubstantivoComMesmoInicio() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "Em quantos dias posso trocar o produto?");
        assertTrue(resultado.encontrou());
        assertEquals("Seção: Trocas e devoluções", resultado.fontes().get(0).localizacao());
    }

    @Test
    void perguntaSemCorrespondenciaNaoRecebeTrechoArbitrario() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "Vocês vendem carros importados?");
        assertEquals(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA, resultado.situacao());
        assertTrue(resultado.trechos().isEmpty());
    }

    @Test
    void coberturaMinimaEvitaTrechoQueSoCompartilhaUmaPalavraComum() {
        // "cartao" aparece no documento, mas "emprestimo" e "consignado" nao: 1 de 3 termos.
        ResultadoBusca resultado = busca.buscar(EMPRESA, "empréstimo consignado no cartão");
        assertEquals(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA, resultado.situacao());
    }

    @Test
    void saudacaoSemTermosNaoConsultaDocumentos() {
        assertEquals(ResultadoBusca.Situacao.PERGUNTA_SEM_TERMOS, busca.buscar(EMPRESA, "Oi, tudo bem?").situacao());
        assertEquals(ResultadoBusca.Situacao.PERGUNTA_SEM_TERMOS, busca.buscar(EMPRESA, "   ").situacao());
        assertEquals(ResultadoBusca.Situacao.PERGUNTA_SEM_TERMOS, busca.buscar(EMPRESA, null).situacao());
        verifyNoInteractions(documentos);
    }

    @Test
    void empresaSemDocumentoAtivoTemSituacaoPropria() {
        when(documentos.ativosDaEmpresa(8L)).thenReturn(List.of());
        assertEquals(ResultadoBusca.Situacao.SEM_DOCUMENTOS, busca.buscar(8L, "prazo de entrega").situacao());
    }

    @Test
    void documentoMarcadoInativoNuncaEntra() {
        Documento inativo = new Documento(12L, "Entregas antigas", "# Prazo\nO prazo de entrega era 30 dias.", 1, false);
        when(documentos.ativosDaEmpresa(EMPRESA)).thenReturn(List.of(inativo));
        assertEquals(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA, busca.buscar(EMPRESA, "prazo de entrega").situacao());
    }

    @Test
    void limitaQuantidadeETamanhoDoContexto() {
        StringBuilder conteudo = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            conteudo.append("# Garantia ").append(i).append('\n')
                    .append("A garantia do produto modelo ").append(i).append(" cobre defeitos de fabricação. ".repeat(15))
                    .append("\n\n");
        }
        when(documentos.ativosDaEmpresa(EMPRESA)).thenReturn(List.of(new Documento(20L, "Garantias", conteudo.toString(), 1, true)));

        ResultadoBusca resultado = busca.buscar(EMPRESA, "garantia de defeito de fabricação");

        assertTrue(resultado.trechos().size() <= BuscaConhecimentoService.MAXIMO_TRECHOS);
        int total = resultado.fontes().stream().mapToInt(f -> f.texto().length()).sum();
        assertTrue(total <= BuscaConhecimentoService.MAXIMO_CARACTERES);
        assertEquals(resultado.fontes().size(), resultado.fontes().stream().map(FonteDisponivel::chave).distinct().count());
    }

    @Test
    void empresaInvalidaERecusada() {
        assertThrows(IllegalArgumentException.class, () -> busca.buscar(null, "prazo"));
        assertThrows(IllegalArgumentException.class, () -> busca.buscar(0L, "prazo"));
        verifyNoInteractions(documentos);
    }

    @Test
    void resultadoSoTemTrechosQuandoEncontrou() {
        assertThrows(IllegalArgumentException.class, () -> new ResultadoBusca(ResultadoBusca.Situacao.ENCONTRADO, List.of()));
        FonteDisponivel fonte = new FonteDisponivel(1L, 2L, "1", "Doc", "t1", null, "Texto");
        assertThrows(IllegalArgumentException.class, () -> new ResultadoBusca(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA,
                List.of(new ResultadoBusca.TrechoEncontrado(fonte, 1.0))));
    }

    @Test
    void radicalReduzPluraisComuns() {
        assertEquals("informacao", BuscaConhecimentoService.radical("informacoes"));
        assertEquals("prazo", BuscaConhecimentoService.radical("prazos"));
        assertEquals("valor", BuscaConhecimentoService.radical("valores"));
        assertEquals("canal", BuscaConhecimentoService.radical("canais"));
        assertEquals("2024", BuscaConhecimentoService.radical("2024"));
    }
}
