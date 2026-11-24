package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.llm.BuscaConhecimentoService;
import com.wxn.chatbot.llm.ConsultaTrechoConhecimento;
import com.wxn.chatbot.llm.ContextoConhecimentoSeguro;
import com.wxn.chatbot.llm.ContextoConhecimentoObsoletoException;
import com.wxn.chatbot.llm.DisponibilidadeFonteConhecimento;
import com.wxn.chatbot.llm.FonteDisponivel;
import com.wxn.chatbot.llm.ResultadoBusca;
import com.wxn.chatbot.llm.SegurancaContextoConhecimento;
import com.wxn.chatbot.model.DocumentoConhecimento;
import com.wxn.chatbot.model.Empresa;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:busca-conhecimento;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class BuscaConhecimentoIntegracaoTests {

    private static final long EMPRESA = 83001L;
    private static final long OUTRA_EMPRESA = 83002L;

    @Autowired EntityManager em;
    @Autowired BuscaConhecimentoService busca;
    @Autowired ConsultaTrechoConhecimento consultaTrecho;
    @Autowired DisponibilidadeFonteConhecimento disponibilidade;
    @Autowired SegurancaContextoConhecimento seguranca;

    private DocumentoConhecimento entregas;
    private DocumentoConhecimento desativado;
    private DocumentoConhecimento daOutraEmpresa;

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(EMPRESA, "Empresa Exemplo");
        Empresa outra = new Empresa(OUTRA_EMPRESA, "Empresa Alfa");
        em.persist(empresa);
        em.persist(outra);
        entregas = new DocumentoConhecimento(empresa, "Política de entregas",
                "# Prazos de entrega\nO prazo de entrega padrão é de 5 dias úteis.\n\n# Trocas\nTrocas em até 7 dias.");
        desativado = new DocumentoConhecimento(empresa, "Frete antigo",
                "# Frete\nO frete para o interior custa 40 reais e o prazo de entrega é de 15 dias.");
        desativado.definirAtivo(false);
        daOutraEmpresa = new DocumentoConhecimento(outra, "Entregas Alfa",
                "# Prazo de entrega\nNa Empresa Alfa o prazo de entrega é de 1 dia.");
        em.persist(entregas);
        em.persist(desativado);
        em.persist(daOutraEmpresa);
        em.flush();
    }

    @Test
    void buscaSoDevolveDocumentosAtivosDaPropriaEmpresa() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "qual o prazo de entrega?");

        assertTrue(resultado.encontrou());
        assertEquals(entregas.getId(), resultado.fontes().get(0).documentoId());
        assertTrue(resultado.fontes().stream().allMatch(f -> f.empresaId().equals(EMPRESA)));
        assertTrue(resultado.fontes().stream().noneMatch(f -> f.documentoId().equals(desativado.getId())));
        assertTrue(resultado.fontes().stream().noneMatch(f -> f.documentoId().equals(daOutraEmpresa.getId())));

        ResultadoBusca outra = busca.buscar(OUTRA_EMPRESA, "qual o prazo de entrega?");
        assertEquals(List.of(daOutraEmpresa.getId()), outra.fontes().stream().map(FonteDisponivel::documentoId).distinct().toList());
    }

    @Test
    void trechoEncontradoPassaPelaBarreiraDeSegurancaSemPerda() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "prazo de entrega");

        ContextoConhecimentoSeguro contexto = seguranca.preparar(EMPRESA, resultado.fontes(), disponibilidade);

        assertEquals(resultado.fontes(), contexto.fontes());
        assertDoesNotThrow(() -> seguranca.revalidar(contexto, disponibilidade));
    }

    @Test
    void consultaDoTrechoConfereEmpresaVersaoEstadoEId() {
        FonteDisponivel fonte = busca.buscar(EMPRESA, "prazo de entrega").fontes().get(0);
        String versao = fonte.documentoVersao();

        assertTrue(consultaTrecho.existe(EMPRESA, entregas.getId(), versao, fonte.trechoId()));
        assertFalse(consultaTrecho.existe(OUTRA_EMPRESA, entregas.getId(), versao, fonte.trechoId()));
        assertFalse(consultaTrecho.existe(EMPRESA, entregas.getId(), "99", fonte.trechoId()));
        assertFalse(consultaTrecho.existe(EMPRESA, entregas.getId(), versao, "t99"));
        assertFalse(consultaTrecho.existe(EMPRESA, entregas.getId(), "abc", fonte.trechoId()));
        assertFalse(consultaTrecho.existe(EMPRESA, desativado.getId(), "1", "t1"));
    }

    @Test
    void editarDocumentoInvalidaTrechosDaVersaoAnteriorEDescartaGeracaoEmAndamento() {
        ResultadoBusca antes = busca.buscar(EMPRESA, "prazo de entrega");
        ContextoConhecimentoSeguro contexto = seguranca.preparar(EMPRESA, antes.fontes(), disponibilidade);

        entregas.atualizar("Política de entregas", "# Prazos de entrega\nO prazo de entrega padrão passou a ser de 3 dias úteis.");
        em.flush();

        assertThrows(ContextoConhecimentoObsoletoException.class, () -> seguranca.revalidar(contexto, disponibilidade));
        FonteDisponivel nova = busca.buscar(EMPRESA, "prazo de entrega").fontes().get(0);
        assertEquals("2", nova.documentoVersao());
        assertTrue(nova.texto().contains("3 dias úteis"));
    }

    @Test
    void desativarDocumentoTiraOTrechoDaBuscaSeguinte() {
        assertTrue(busca.buscar(EMPRESA, "trocas").encontrou());

        entregas.definirAtivo(false);
        em.flush();

        assertEquals(ResultadoBusca.Situacao.SEM_DOCUMENTOS, busca.buscar(EMPRESA, "trocas").situacao());
        assertFalse(disponibilidade.estaDisponivel(EMPRESA, entregas.getId(), "1", "t2"));
    }

    @Test
    void perguntaSemRelacaoNaoTrazTrecho() {
        ResultadoBusca resultado = busca.buscar(EMPRESA, "vocês aceitam bitcoin?");
        assertEquals(ResultadoBusca.Situacao.SEM_CORRESPONDENCIA, resultado.situacao());
        assertTrue(resultado.fontes().isEmpty());
    }
}
