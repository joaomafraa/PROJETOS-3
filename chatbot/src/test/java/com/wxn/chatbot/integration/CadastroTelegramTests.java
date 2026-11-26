package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.telegram.MensagemTelegram;
import com.wxn.chatbot.telegram.ProcessarMensagemTelegramService;
import com.wxn.chatbot.telegram.RespostaTelegramService;
import com.wxn.chatbot.telegram.TelegramCliente;
import com.wxn.chatbot.telegram.TelegramCliente.Teclado;

import jakarta.persistence.EntityManager;

// HU07.1 e HU07.2 pelo canal Telegram: do primeiro contato ate o cliente identificado ou cadastrado.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cadastro-telegram;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class CadastroTelegramTests {

    private static final long EMPRESA = 82001L;
    private static final long OUTRA_EMPRESA = 82002L;
    private static final long CHATBOT = 82001L;
    private static final long USUARIO = 888L;
    private static final String TELEFONE_CONHECIDO = "+5581977770001";
    private static final String TELEFONE_NOVO = "+5581966660002";

    @Autowired EntityManager em;
    @Autowired ProcessarMensagemTelegramService processamento;
    @MockitoBean TelegramCliente telegram;
    @MockitoBean RespostaTelegramService respostas;

    private long proximaAtualizacao = 1000;

    @Test
    void abreChamadoUmaVezEConfirmaProtocolo() {
        enviar("Oi"); enviarContato(TELEFONE_CONHECIDO); enviar("/chamado");
        var descricao = new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Ana", "Não consigo acessar o sistema desde ontem.");
        assertTrue(processamento.processar(EMPRESA, CHATBOT, descricao));
        assertFalse(processamento.processar(EMPRESA, CHATBOT, descricao));
        em.flush();
        assertEquals(1L, numero("select count(*) from tickets where empresa_id = 82001"));
        String protocolo = (String) em.createNativeQuery("select protocolo from tickets where empresa_id = 82001").getSingleResult();
        verify(telegram).enviarMensagem(eq(USUARIO), contains(protocolo));
        assertEquals(82001L, numero("select cliente_id from tickets where empresa_id = 82001"));
    }

    @Test
    void cancelarOuDescricaoCurtaNaoAbreChamado() {
        enviar("Oi"); enviarContato(TELEFONE_CONHECIDO); enviar("/chamado"); enviar("erro"); enviar("/cancelar");
        em.flush();
        assertEquals(0L, numero("select count(*) from tickets where empresa_id = 82001"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("cancelada"));
    }

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(EMPRESA, "Empresa Exemplo");
        Empresa outra = new Empresa(OUTRA_EMPRESA, "Empresa Alfa");
        em.persist(empresa);
        em.persist(outra);
        em.persist(new Chatbot(CHATBOT, "Bot", empresa, "Ola"));
        Cliente marina = new Cliente(82001L, TELEFONE_CONHECIDO);
        marina.alterarNome("Marina Costa");
        marina.vincularEmpresa(empresa);
        em.persist(marina);
        Cliente daOutraEmpresa = new Cliente(82002L, TELEFONE_NOVO);
        daOutraEmpresa.alterarNome("Paulo Lima");
        daOutraEmpresa.vincularEmpresa(outra);
        em.persist(daOutraEmpresa);
        em.flush();
        when(respostas.respostaExistenteOuPendente(anyLong(), anyLong())).thenReturn("Resposta da IA");
    }

    @Test
    void primeiraMensagemPedeTelefoneComBotaoEAntesDaIa() {
        assertTrue(enviar("/start"));

        verify(telegram).enviarMensagem(USUARIO, textoPedirTelefone(), Teclado.PEDIR_TELEFONE);
        verifyNoInteractions(respostas);
        assertEquals("AGUARDANDO_TELEFONE", etapa());
        assertNull(clienteDaConversa().getNumeroTelefone());
    }

    @Test
    void numeroConhecidoLigaContatoEConversaAoCadastroExistente() {
        enviar("Oi");
        assertTrue(enviarContato(TELEFONE_CONHECIDO));

        verify(telegram).enviarMensagem(eq(USUARIO), contains("Localizei um cadastro"), eq(Teclado.REMOVER));
        Cliente cliente = clienteDaConversa();
        assertEquals(82001L, cliente.getId());
        assertFalse(cliente.isIdentidadeConfirmada(), "Telefone reconhecido não comprova identidade.");
        assertEquals("CONCLUIDO", etapa());
        assertEquals(82001L, numero("select cliente_id from telegram_contatos where empresa_id = 82001 and usuario_id = 888"));
        assertEquals(1L, numero("select count(*) from clientes where numero_telefone = '" + TELEFONE_CONHECIDO + "'"));

        // Identificado: a conversa segue para a IA.
        assertTrue(enviar("Quero consultar meu plano"));
        verify(telegram).enviarMensagem(USUARIO, "Resposta da IA");
    }

    @Test
    void numeroDesconhecidoColetaNomeEEmpresaECadastraPeloServico() {
        enviar("Olá, quero conhecer os serviços");
        enviarContato("+5581955550003");
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Como posso chamar você?"), eq(Teclado.REMOVER));
        assertEquals("AGUARDANDO_NOME", etapa());

        enviar("12345");
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Não entendi seu nome"));
        assertEquals("AGUARDANDO_NOME", etapa());

        enviar("Ana Souza");
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Prazer, Ana Souza"));
        assertEquals("AGUARDANDO_EMPRESA", etapa());

        enviar("Empresa Beta");
        verify(telegram).enviarMensagem(eq(USUARIO), contains("Obrigado, Ana Souza! Seu cadastro foi concluído"));
        Cliente cliente = clienteDaConversa();
        assertEquals("+5581955550003", cliente.getNumeroTelefone());
        assertEquals("Ana Souza", cliente.getNome());
        assertEquals("Empresa Beta", cliente.getEmpresaInformada());
        assertEquals(EMPRESA, cliente.getEmpresa().getId());
        assertFalse(cliente.isIdentidadeConfirmada());
        assertEquals("CONCLUIDO", etapa());
        assertNull(em.createNativeQuery("select telefone_informado from telegram_contatos where usuario_id = 888").getSingleResult());
        verifyNoInteractions(respostas);
    }

    @Test
    void pularEmpresaConcluiSemEmpresaInformada() {
        enviar("Oi");
        enviarContato("+5581955550004");
        enviar("Bia Santos");
        enviar("/pular");

        Cliente cliente = clienteDaConversa();
        assertEquals("Bia Santos", cliente.getNome());
        assertNull(cliente.getEmpresaInformada());
        assertEquals("CONCLUIDO", etapa());
    }

    @Test
    void numeroDigitadoNaoLocalizaCadastro() {
        enviar("Oi");
        assertTrue(enviar("+55 81 97777-0001"));

        verify(telegram).enviarMensagem(eq(USUARIO), contains("não usamos número digitado"), eq(Teclado.PEDIR_TELEFONE));
        assertNull(clienteDaConversa().getNumeroTelefone());
        assertEquals("AGUARDANDO_TELEFONE", etapa());
    }

    @Test
    void contatoDeOutraPessoaNaoLocalizaCadastro() {
        enviar("Oi");
        Map<String, Object> update = Map.of("update_id", proximaAtualizacao++, "message", Map.of(
                "chat", Map.of("id", USUARIO, "type", "private"),
                "from", Map.of("id", USUARIO, "first_name", "Ana"),
                "contact", Map.of("phone_number", "5581977770001", "user_id", 999L)));
        MensagemTelegram mensagem = MensagemTelegram.interpretar(update).orElseThrow();
        assertNull(mensagem.telefone());

        assertTrue(processamento.processar(EMPRESA, CHATBOT, mensagem));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("seu próprio número"), eq(Teclado.PEDIR_TELEFONE));
        assertNull(clienteDaConversa().getNumeroTelefone());
        assertEquals("AGUARDANDO_TELEFONE", etapa());
    }

    @Test
    void telefoneDeOutraEmpresaNaoEhReconhecidoECadastraNaEmpresaDoBot() {
        enviar("Oi");
        enviarContato(TELEFONE_NOVO); // so existe na OUTRA_EMPRESA
        assertEquals("AGUARDANDO_NOME", etapa());
        enviar("Paulo Lima");
        enviar("/pular");

        Cliente cliente = clienteDaConversa();
        assertNotEquals(82002L, cliente.getId());
        assertEquals(EMPRESA, cliente.getEmpresa().getId());
        Cliente daOutraEmpresa = em.find(Cliente.class, 82002L);
        assertEquals(OUTRA_EMPRESA, daOutraEmpresa.getEmpresa().getId());
        assertEquals(2L, numero("select count(*) from clientes where numero_telefone = '" + TELEFONE_NOVO + "'"));
    }

    @Test
    void atendenteContinuaDisponivelEPausaOCadastro() {
        enviar("Oi");
        assertTrue(enviar("/atendente"));
        verify(telegram).enviarMensagem(eq(USUARIO), contains("atendimento humano"));
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, conversa().getStatus());

        // Na fila do atendente, o bot nao responde nem pede telefone
        // (o servico real de resposta devolve null fora do atendimento automatico).
        when(respostas.respostaExistenteOuPendente(anyLong(), anyLong())).thenReturn(null);
        assertFalse(enviarContato(TELEFONE_CONHECIDO));
        verify(telegram, times(1)).enviarMensagem(anyLong(), anyString(), any(Teclado.class)); // so o pedido inicial
        assertEquals("AGUARDANDO_TELEFONE", etapa());
        assertNull(clienteDaConversa().getNumeroTelefone());
    }

    @Test
    void repetirAMesmaAtualizacaoNaoAplicaAEtapaDuasVezes() {
        enviar("Oi");
        enviarContato("+5581955550005");
        MensagemTelegram nome = new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Ana", "Carla Dias");
        assertTrue(processamento.processar(EMPRESA, CHATBOT, nome));
        assertEquals("AGUARDANDO_EMPRESA", etapa());

        assertFalse(processamento.processar(EMPRESA, CHATBOT, nome));
        assertEquals("AGUARDANDO_EMPRESA", etapa());
        assertNull(clienteDaConversa().getEmpresaInformada());
        verify(telegram, times(1)).enviarMensagem(eq(USUARIO), contains("Prazer, Carla Dias"));
    }

    @Test
    void contatoJaIdentificadoNaoPassaDeNovoPelaIdentificacao() {
        enviar("Oi");
        enviarContato(TELEFONE_CONHECIDO);
        enviarContato(TELEFONE_CONHECIDO);

        verify(telegram, times(1)).enviarMensagem(eq(USUARIO), contains("Localizei um cadastro"), any());
        assertEquals(82001L, clienteDaConversa().getId());
    }

    private boolean enviar(String texto) {
        return processamento.processar(EMPRESA, CHATBOT,
                new MensagemTelegram(proximaAtualizacao++, USUARIO, USUARIO, "Ana", texto));
    }

    private boolean enviarContato(String telefone) {
        Map<String, Object> update = Map.of("update_id", proximaAtualizacao++, "message", Map.of(
                "chat", Map.of("id", USUARIO, "type", "private"),
                "from", Map.of("id", USUARIO, "first_name", "Ana"),
                "contact", Map.of("phone_number", telefone, "user_id", USUARIO)));
        return processamento.processar(EMPRESA, CHATBOT, MensagemTelegram.interpretar(update).orElseThrow());
    }

    private Conversa conversa() {
        em.flush();
        em.clear();
        long id = numero("select conversa_id from telegram_contatos where empresa_id = 82001 and usuario_id = 888");
        return em.find(Conversa.class, id);
    }

    private Cliente clienteDaConversa() {
        return conversa().getCliente();
    }

    private String etapa() {
        em.flush();
        return (String) em.createNativeQuery(
                "select etapa_cadastro from telegram_contatos where empresa_id = 82001 and usuario_id = 888").getSingleResult();
    }

    private long numero(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private static String textoPedirTelefone() {
        return "Olá! Para localizar seu cadastro, toque em \"Compartilhar meu telefone\" abaixo. "
                + "Usamos o número só para identificar seu atendimento.";
    }
}
