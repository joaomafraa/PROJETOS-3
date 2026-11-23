package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.FontesMensagem;
import com.wxn.chatbot.llm.FonteDisponivel;
import com.wxn.chatbot.llm.ReferenciasResposta;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.FonteRespostaRepository;
import com.wxn.chatbot.telegram.RespostaTelegramService;

// HU02.2: registro, consulta e isolamento das fontes das respostas automaticas.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fontes-resposta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexus.telegram.polling.enabled=false"
})
@ActiveProfiles("test")
class FontesRespostaServiceTests {
    @Autowired private FontesRespostaService fontes;
    @Autowired private FonteRespostaRepository repositorio;
    @Autowired private ConversaRepository conversas;
    @Autowired private RespostaTelegramService respostaTelegram;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager manager;

    private TransactionTemplate transacao;

    // Empresa 1: conversas 1 e 2, atendentes 1 (ativo) e 3 (inativo). Empresa 2: conversa 3, atendente 2.
    private static final FonteDisponivel HORARIO = new FonteDisponivel(1L, 10L, "v2", "Política de atendimento",
            "t-3", "Seção 2 - Horários", "Atendemos de segunda a sexta, das 8h às 18h.");
    private static final FonteDisponivel PRAZO = new FonteDisponivel(1L, 11L, "v1", "Manual de entregas",
            "t-7", null, "O prazo de entrega é de 5 dias úteis.");
    private static final FonteDisponivel EXTERNA = new FonteDisponivel(2L, 90L, "v1", "Documento da outra empresa",
            "t-1", null, "Conteúdo restrito da empresa 2.");

    @BeforeEach
    void preparar() {
        transacao = new TransactionTemplate(manager);
        transacao.executeWithoutResult(status -> {
            entityManager.createQuery("delete from FonteResposta").executeUpdate();
            entityManager.createNativeQuery("delete from mensagens").executeUpdate();
            entityManager.createNativeQuery("delete from conversas").executeUpdate();
            entityManager.createNativeQuery("delete from clientes").executeUpdate();
            entityManager.createNativeQuery("delete from chatbots").executeUpdate();
            entityManager.createNativeQuery("delete from atendentes").executeUpdate();
            entityManager.createNativeQuery("delete from empresas").executeUpdate();

            Empresa empresa = new Empresa(1L, "NEXUS");
            Empresa outra = new Empresa(2L, "Outra");
            entityManager.persist(empresa);
            entityManager.persist(outra);
            Atendente ana = new Atendente(1L, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ADMINISTRADOR);
            entityManager.persist(ana);
            entityManager.persist(new Atendente(2L, "Externo", "externo@example.com", outra, Atendente.PerfilAcesso.ATENDENTE));
            Atendente inativo = new Atendente(3L, "Inativo", "inativo@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE);
            entityManager.persist(inativo);
            inativo.desativar(ana);
            Chatbot bot = new Chatbot(1L, "Assistente", empresa, "Olá");
            Chatbot botExterno = new Chatbot(2L, "Bot externo", outra, "Olá");
            entityManager.persist(bot);
            entityManager.persist(botExterno);
            Cliente marina = new Cliente(1L, "+5581999990001");
            marina.vincularEmpresa(empresa);
            Cliente carlos = new Cliente(2L, "+5581999990002");
            carlos.vincularEmpresa(empresa);
            Cliente externo = new Cliente(3L, "+5511999990003");
            externo.vincularEmpresa(outra);
            entityManager.persist(marina);
            entityManager.persist(carlos);
            entityManager.persist(externo);

            Conversa conversa = new Conversa(1L, marina, empresa, bot);
            conversa.adicionarMensagem(1L, "Qual o horário e o prazo?", Mensagem.TipoRemetente.CLIENTE);
            // Resposta antiga, de antes do registro de fontes.
            conversa.adicionarMensagem(2L, "Olá! Vou verificar.", Mensagem.TipoRemetente.CHATBOT);
            conversa.adicionarMensagem(3L, "Obrigada", Mensagem.TipoRemetente.CLIENTE);
            entityManager.persist(conversa);
            Conversa outraConversa = new Conversa(2L, carlos, empresa, bot);
            outraConversa.adicionarMensagem(1L, "Oi", Mensagem.TipoRemetente.CLIENTE);
            entityManager.persist(outraConversa);
            Conversa conversaExterna = new Conversa(3L, externo, outra, botExterno);
            conversaExterna.adicionarMensagem(1L, "Oi", Mensagem.TipoRemetente.CLIENTE);
            entityManager.persist(conversaExterna);
        });
    }

    // Faz o que a geracao fundamentada fara: valida as referencias, salva a resposta e registra as fontes juntas.
    private ReferenciasResposta.Resultado responderComFontes(long conversaId, long empresaId, long mensagemId,
            String textoDaIa, List<FonteDisponivel> fornecidas) {
        return transacao.execute(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(conversaId, empresaId).orElseThrow();
            var resultado = ReferenciasResposta.extrair(textoDaIa, fornecidas);
            conversa.adicionarMensagem(mensagemId, resultado.texto(), Mensagem.TipoRemetente.CHATBOT);
            fontes.registrar(conversa, mensagemId, fornecidas, resultado.citadas());
            return resultado;
        });
    }

    private Atendente atendente(long id) {
        return transacao.execute(status -> entityManager.find(Atendente.class, id));
    }

    @Test
    void registraCitadasERecuperadasEMostraNoPainel() {
        var resultado = responderComFontes(1L, 1L, 4L,
                "O prazo é de 5 dias úteis [F2]. Atendemos das 8h às 18h [F1]. Também [F9].", List.of(HORARIO, PRAZO));

        assertEquals("O prazo é de 5 dias úteis [1]. Atendemos das 8h às 18h [2]. Também.", resultado.texto());
        assertEquals(1, resultado.referenciasInvalidas());

        List<FontesMensagem> painel = fontes.listarPorConversa(1L, atendente(1L));
        assertEquals(1, painel.size(), "A resposta antiga sem fontes nao aparece com fontes.");
        FontesMensagem registro = painel.getFirst();
        assertEquals(4L, registro.mensagemId());
        assertEquals(List.of(1, 2), registro.citadas().stream().map(FontesMensagem.Fonte::numero).toList());
        assertEquals(List.of("Manual de entregas", "Política de atendimento"),
                registro.citadas().stream().map(FontesMensagem.Fonte::documentoTitulo).toList());
        assertEquals(List.of("t-3", "t-7"), registro.recuperadas().stream().map(FontesMensagem.Fonte::trechoId).toList());
        assertTrue(registro.recuperadas().stream().allMatch(fonte -> fonte.numero() == null));
        FontesMensagem.Fonte horario = registro.citadas().get(1);
        assertEquals("v2", horario.documentoVersao());
        assertEquals("Seção 2 - Horários", horario.localizacao());
        assertEquals("Atendemos de segunda a sexta, das 8h às 18h.", horario.excerto());
    }

    @Test
    void mensagemEnviadaAoTelegramTrazTextoLimpoEFontes() {
        responderComFontes(1L, 1L, 4L, "Atendemos das 8h às 18h [F1].", List.of(HORARIO, PRAZO));

        String texto = respostaTelegram.respostaExistenteOuPendente(1L, 1L);

        assertEquals("Atendemos das 8h às 18h [1].\n\nFontes:\n[1] Política de atendimento — Seção 2 - Horários", texto);
    }

    @Test
    void respostaSemCitacaoContinuaFuncionandoSemListaDeFontes() {
        responderComFontes(1L, 1L, 4L, "Não encontrei essa informação nos documentos.", List.of(HORARIO));

        assertEquals("Não encontrei essa informação nos documentos.", respostaTelegram.respostaExistenteOuPendente(1L, 1L));
        FontesMensagem registro = fontes.listarPorConversa(1L, atendente(1L)).getFirst();
        assertTrue(registro.citadas().isEmpty());
        assertEquals(1, registro.recuperadas().size(), "Trecho consultado fica registrado, mas nao como citado.");
    }

    @Test
    void citacaoDeTrechoNaoFornecidoEhRecusadaENadaESalvo() {
        var inventada = new ReferenciasResposta.Citacao(1, PRAZO);

        assertThrows(IllegalArgumentException.class, () -> transacao.executeWithoutResult(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(1L, 1L).orElseThrow();
            conversa.adicionarMensagem(4L, "Prazo de 5 dias [1].", Mensagem.TipoRemetente.CHATBOT);
            fontes.registrar(conversa, 4L, List.of(HORARIO), List.of(inventada));
        }));

        assertEquals(0, repositorio.count());
        int mensagens = transacao.execute(status -> conversas.findById(1L).orElseThrow().getMensagens().size());
        assertEquals(3, mensagens,
                "A resposta tambem e descartada: fontes e resposta sao salvas juntas.");
    }

    @Test
    void fonteDeOutraEmpresaEhRecusada() {
        assertThrows(SecurityException.class,
                () -> responderComFontes(1L, 1L, 4L, "Conteúdo [F1].", List.of(EXTERNA)));
        assertEquals(0, repositorio.count());
    }

    @Test
    void somenteRespostasDoChatbotRecebemFontes() {
        assertThrows(IllegalArgumentException.class, () -> transacao.executeWithoutResult(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(1L, 1L).orElseThrow();
            fontes.registrar(conversa, 3L, List.of(HORARIO), List.of());
        }));
        assertThrows(IllegalArgumentException.class, () -> transacao.executeWithoutResult(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(1L, 1L).orElseThrow();
            fontes.registrar(conversa, 99L, List.of(HORARIO), List.of());
        }));
    }

    @Test
    void naoRegistraDuasVezesNemForaDeTransacao() {
        responderComFontes(1L, 1L, 4L, "Horário [F1].", List.of(HORARIO));

        assertThrows(IllegalStateException.class, () -> transacao.executeWithoutResult(status -> {
            Conversa conversa = conversas.buscarParaAtualizar(1L, 1L).orElseThrow();
            fontes.registrar(conversa, 4L, List.of(HORARIO), List.of());
        }));
        Conversa solta = transacao.execute(status -> conversas.findById(1L).orElseThrow());
        assertThrows(IllegalTransactionStateException.class, () -> fontes.registrar(solta, 4L, List.of(HORARIO), List.of()));
    }

    @Test
    void limitaQuantidadeDeTrechosPorResposta() {
        List<FonteDisponivel> muitas = java.util.stream.IntStream.rangeClosed(1, FontesRespostaService.MAXIMO_FONTES_POR_RESPOSTA + 1)
                .mapToObj(i -> new FonteDisponivel(1L, 10L, "v1", "Doc", "t-" + i, null, "texto " + i)).toList();

        assertThrows(IllegalArgumentException.class, () -> responderComFontes(1L, 1L, 4L, "Ok.", muitas));
    }

    @Test
    void outraEmpresaNaoConsultaFontesEAtendenteInativoEhBloqueado() {
        responderComFontes(1L, 1L, 4L, "Horário [F1].", List.of(HORARIO));

        assertThrows(IllegalArgumentException.class, () -> fontes.listarPorConversa(1L, atendente(2L)));
        assertThrows(SecurityException.class, () -> fontes.listarPorConversa(1L, atendente(3L)));
        assertThrows(IllegalArgumentException.class, () -> fontes.listarPorConversa(0L, atendente(1L)));
        assertTrue(fontes.citadas(2L, 1L, 4L).isEmpty(), "Consulta com outra empresa nao devolve nada.");
        assertTrue(fontes.listarPorConversa(2L, atendente(1L)).isEmpty(), "Outra conversa da mesma empresa nao mistura fontes.");
    }

    @Test
    void registroGuardaCopiaDaOrigemMesmoSemODocumento() {
        // Ainda nao existe tabela de documentos: o registro nao depende dela, entao desativar
        // ou trocar a versao de um documento nao apaga a origem das respostas antigas.
        responderComFontes(1L, 1L, 4L, "Horário [F1].", List.of(HORARIO));
        var novaVersao = new FonteDisponivel(1L, 10L, "v3", "Política de atendimento (revisada)", "t-3",
                "Seção 2", "Atendemos das 9h às 17h.");
        responderComFontes(1L, 1L, 5L, "Agora o horário é outro [F1].", List.of(novaVersao));

        List<FontesMensagem> painel = fontes.listarPorConversa(1L, atendente(1L));
        assertEquals("v2", painel.get(0).citadas().getFirst().documentoVersao());
        assertEquals("Atendemos de segunda a sexta, das 8h às 18h.", painel.get(0).citadas().getFirst().excerto());
        assertEquals("v3", painel.get(1).citadas().getFirst().documentoVersao());
    }
}
