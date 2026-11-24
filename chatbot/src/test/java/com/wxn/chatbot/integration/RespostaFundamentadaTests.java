package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.llm.ContextoConhecimentoObsoletoException;
import com.wxn.chatbot.llm.FalhaLlmException;
import com.wxn.chatbot.llm.FonteDisponivel;
import com.wxn.chatbot.llm.ProvedorLlm;
import com.wxn.chatbot.llm.RespostaAutomaticaService;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.DocumentoConhecimento;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.service.FontesRespostaService;
import com.wxn.chatbot.telegram.RespostaTelegramService;

import jakarta.persistence.EntityManager;

// HU02.1/HU02.2: pergunta do cliente -> busca -> seguranca -> politica -> provedor -> mensagem e fontes salvas.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:resposta-fundamentada;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class RespostaFundamentadaTests {
    private static final AtomicLong IDS = new AtomicLong(84000);

    @Autowired RespostaAutomaticaService respostas;
    @Autowired RespostaTelegramService respostaTelegram;
    @Autowired FontesRespostaService fontes;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean ProvedorLlm provedor;

    private TransactionTemplate tx;
    private long id;
    private long documentoId;
    private long documentoOutraEmpresaId;

    @BeforeEach
    void preparar() {
        id = IDS.getAndAdd(10);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Empresa empresa = new Empresa(id, "Empresa Exemplo");
            Empresa outra = new Empresa(id + 1, "Empresa Alfa");
            em.persist(empresa);
            em.persist(outra);
            Cliente cliente = new Cliente(id, "+5581900000" + (id % 1000));
            cliente.vincularEmpresa(empresa);
            em.persist(cliente);
            Chatbot bot = new Chatbot(id, "Bot", empresa, "Olá");
            em.persist(bot);
            em.persist(new Conversa(id, cliente, empresa, bot));
            DocumentoConhecimento entregas = new DocumentoConhecimento(empresa, "Política de entregas",
                    "# Prazos de entrega\nO prazo de entrega padrão é de 5 dias úteis.\n\n# Trocas\nTrocas em até 7 dias corridos.");
            DocumentoConhecimento alfa = new DocumentoConhecimento(outra, "Entregas Alfa",
                    "# Prazo de entrega\nNa Empresa Alfa o prazo de entrega é de 1 dia.");
            em.persist(entregas);
            em.persist(alfa);
            em.flush();
            documentoId = entregas.getId();
            documentoOutraEmpresaId = alfa.getId();
        });
    }

    @Test
    void perguntaCobertaUsaTrechoOficialESalvaRespostaComFontes() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenReturn("O prazo padrão é de 5 dias úteis [F1].");

        String resposta = respostas.responder(id, id, 2L);

        assertEquals("O prazo padrão é de 5 dias úteis [1].", resposta);
        List<FonteDisponivel> enviadas = fontesEnviadas();
        assertEquals(documentoId, enviadas.get(0).documentoId());
        assertEquals("Seção: Prazos de entrega", enviadas.get(0).localizacao());
        assertTrue(enviadas.stream().allMatch(f -> f.empresaId().equals(id)));
        assertTrue(enviadas.stream().noneMatch(f -> f.documentoId().equals(documentoOutraEmpresaId)));
        assertEquals(resposta, ultimaMensagem().getConteudo());
        var citadas = fontes.citadas(id, id, 2L);
        assertEquals(1, citadas.size());
        assertEquals("Política de entregas", citadas.get(0).documentoTitulo());
    }

    @Test
    void historicoDaConversaEEnviadoAoProvedor() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenReturn("São 5 dias úteis [F1].");
        respostas.responder(id, id, 2L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProvedorLlm.TrechoConversa>> historico = ArgumentCaptor.forClass(List.class);
        verify(provedor).responder(historico.capture(), anyList());
        assertEquals(List.of(new ProvedorLlm.TrechoConversa("CLIENTE", "Qual o prazo de entrega?")), historico.getValue());
    }

    @Test
    void perguntaSemFonteInformaLimitacaoSemChamarAIa() {
        perguntar("Vocês aceitam bitcoin?");

        String resposta = respostas.responder(id, id, 2L);

        assertTrue(resposta.contains("Não encontrei informação oficial suficiente"));
        assertTrue(resposta.contains("/atendente"));
        verify(provedor, never()).responder(anyList(), anyList());
        verify(provedor, never()).responder(anyList());
        assertEquals(resposta, ultimaMensagem().getConteudo());
        assertTrue(fontes.citadas(id, id, 2L).isEmpty());
    }

    @Test
    void saudacaoSemPerguntaPedeEsclarecimento() {
        perguntar("Oi, tudo bem?");

        String resposta = respostas.responder(id, id, 2L);

        assertTrue(resposta.startsWith("Pode explicar melhor sua dúvida?"));
        verify(provedor, never()).responder(anyList(), anyList());
    }

    @Test
    void trechoQueCobrePoucoDaPerguntaNaoBastaParaResponder() {
        perguntar("Qual o prazo de entrega internacional por navio?");

        String resposta = respostas.responder(id, id, 2L);

        assertTrue(resposta.contains("não são suficientes para responder com segurança"));
        verify(provedor, never()).responder(anyList(), anyList());
    }

    @Test
    void referenciaInventadaEDescartada() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenReturn("São 5 dias úteis [F1] e frete grátis [F9].");

        String resposta = respostas.responder(id, id, 2L);

        assertEquals("São 5 dias úteis [1] e frete grátis.", resposta);
        assertEquals(1, fontes.citadas(id, id, 2L).size());
    }

    @Test
    void documentoDesativadoDuranteAGeracaoDescartaAResposta() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenAnswer(invocacao -> {
            tx.executeWithoutResult(s -> em.find(DocumentoConhecimento.class, documentoId).definirAtivo(false));
            return "São 5 dias úteis [F1].";
        });

        assertThrows(ContextoConhecimentoObsoletoException.class, () -> respostas.responder(id, id, 2L));
        assertEquals(1, mensagens().size());
    }

    @Test
    void assuncaoHumanaDuranteAGeracaoDescartaAResposta() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenAnswer(invocacao -> {
            tx.executeWithoutResult(s -> em.find(Conversa.class, id).solicitarAtendimentoHumano());
            return "São 5 dias úteis [F1].";
        });

        assertThrows(IllegalStateException.class, () -> respostas.responder(id, id, 2L));
        assertEquals(1, mensagens().size());
        assertTrue(fontes.citadas(id, id, 2L).isEmpty());
    }

    @Test
    void respostaVaziaOuSoComRotuloNaoESalva() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenReturn("  ");
        assertThrows(FalhaLlmException.class, () -> respostas.responder(id, id, 2L));

        when(provedor.responder(anyList(), anyList())).thenReturn("[F1]");
        assertThrows(FalhaLlmException.class, () -> respostas.responder(id, id, 2L));
        assertEquals(1, mensagens().size());
    }

    @Test
    void conversaNaFilaHumanaNaoChamaABuscaNemAIa() {
        perguntar("Qual o prazo de entrega?");
        tx.executeWithoutResult(s -> em.find(Conversa.class, id).solicitarAtendimentoHumano());

        assertThrows(IllegalStateException.class, () -> respostas.responder(id, id, 2L));
        verifyNoInteractions(provedor);
    }

    @Test
    void telegramRecebeTextoComAListaDeFontes() {
        perguntar("Qual o prazo de entrega?");
        when(provedor.responder(anyList(), anyList())).thenReturn("O prazo padrão é de 5 dias úteis [F1].");

        String texto = respostaTelegram.gerar(id, id);

        assertTrue(texto.startsWith("O prazo padrão é de 5 dias úteis [1]."));
        assertTrue(texto.contains("Política de entregas"));
    }

    private void perguntar(String texto) {
        tx.executeWithoutResult(s -> em.find(Conversa.class, id).adicionarMensagem(1L, texto, Mensagem.TipoRemetente.CLIENTE));
    }

    @SuppressWarnings("unchecked")
    private List<FonteDisponivel> fontesEnviadas() {
        ArgumentCaptor<List<FonteDisponivel>> captor = ArgumentCaptor.forClass(List.class);
        verify(provedor).responder(anyList(), captor.capture());
        return captor.getValue();
    }

    private List<Mensagem> mensagens() {
        return tx.execute(s -> List.copyOf(em.find(Conversa.class, id).getMensagens()));
    }

    private Mensagem ultimaMensagem() {
        List<Mensagem> todas = mensagens();
        return todas.get(todas.size() - 1);
    }
}
