package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.ResumoEstruturadoConversa.TrechoMensagem;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.service.ResumoEstruturadoConversaService;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:resumo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ResumoEstruturadoConversaTests {
    @Autowired private EntityManager entityManager;
    @Autowired private ResumoEstruturadoConversaService service;

    @BeforeEach
    void prepararDadosPersistidos() {
        Empresa empresa = new Empresa(1L, "Empresa A");
        Empresa outra = new Empresa(2L, "Empresa B");
        entityManager.persist(empresa);
        entityManager.persist(outra);
        entityManager.persist(new Atendente(1L, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
        entityManager.persist(new Atendente(2L, "Admin", "admin@example.com", empresa, Atendente.PerfilAcesso.ADMINISTRADOR));
        entityManager.persist(new Atendente(3L, "Outra", "outra@example.com", outra, Atendente.PerfilAcesso.ATENDENTE));
        Cliente cliente = new Cliente(1L, "+5581999999999");
        cliente.vincularEmpresa(empresa);
        entityManager.persist(cliente);
        Chatbot bot = new Chatbot(1L, "Bot", empresa, "Ola");
        entityManager.persist(bot);
        entityManager.persist(new Conversa(1L, cliente, empresa, bot));

        Conversa comMensagens = new Conversa(2L, cliente, empresa, bot);
        comMensagens.adicionarMensagem(40L, "Olá, preciso de ajuda 😀" + "x".repeat(200), Mensagem.TipoRemetente.CLIENTE);
        comMensagens.adicionarMensagem(10L, "Como posso ajudar?", Mensagem.TipoRemetente.CHATBOT);
        comMensagens.adicionarMensagem(30L, "Não consigo entrar", Mensagem.TipoRemetente.CLIENTE);
        comMensagens.solicitarAtendimentoHumano();
        comMensagens.iniciarAtendimentoHumano(entityManager.find(Atendente.class, 1L));
        comMensagens.adicionarMensagem(20L, "Vou verificar", Mensagem.TipoRemetente.ATENDENTE,
                entityManager.find(Atendente.class, 1L));
        entityManager.persist(comMensagens);
        Conversa apenasChatbot = new Conversa(3L, cliente, empresa, bot);
        apenasChatbot.adicionarMensagem(1L, "Ola!", Mensagem.TipoRemetente.CHATBOT);
        entityManager.persist(apenasChatbot);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void conversaVaziaEClienteIncompletoIndicamDadosAusentes() {
        var resumo = service.gerar(1L, entityManager.find(Atendente.class, 1L));
        assertEquals(1L, resumo.conversaId());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, resumo.status());
        assertNotNull(resumo.dataInicio());
        assertEquals("+5581999999999", resumo.cliente().telefone());
        assertNull(resumo.cliente().nome());
        assertNull(resumo.cliente().email());
        assertNull(resumo.trechoInicialCliente());
        assertTrue(resumo.mensagensRecentes().isEmpty());
        assertEquals(List.of("cliente.nome", "cliente.email", "trechoInicialCliente", "mensagensRecentes"),
                resumo.dadosAusentes());
    }

    @Test
    void selecionaTrechosLiteraisPelaOrdemPersistidaETruncaSemQuebrarUnicode() {
        var resumo = service.gerar(2L, entityManager.find(Atendente.class, 1L));
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, resumo.status());
        assertEquals(40L, resumo.trechoInicialCliente().mensagemId());
        assertTrue(resumo.trechoInicialCliente().trecho().startsWith("Olá, preciso de ajuda 😀"));
        assertTrue(resumo.trechoInicialCliente().truncado());
        assertEquals(200, resumo.trechoInicialCliente().trecho().codePointCount(0,
                resumo.trechoInicialCliente().trecho().length()));
        assertEquals(List.of(10L, 30L, 20L), resumo.mensagensRecentes().stream().map(TrechoMensagem::mensagemId).toList());
        assertEquals(List.of(Mensagem.TipoRemetente.CHATBOT, Mensagem.TipoRemetente.CLIENTE,
                Mensagem.TipoRemetente.ATENDENTE), resumo.mensagensRecentes().stream().map(TrechoMensagem::remetente).toList());
        assertEquals("Não consigo entrar", resumo.mensagensRecentes().get(1).trecho());
        assertNotNull(resumo.mensagensRecentes().get(1).dataEnvio());
        assertThrows(UnsupportedOperationException.class, () -> resumo.mensagensRecentes().clear());
    }

    @Test
    void naoConfundeMensagemDoChatbotComDemandaDoCliente() {
        var resumo = service.gerar(3L, entityManager.find(Atendente.class, 1L));
        assertNull(resumo.trechoInicialCliente());
        assertTrue(resumo.dadosAusentes().contains("trechoInicialCliente"));
        assertFalse(resumo.dadosAusentes().contains("mensagensRecentes"));
        assertEquals("Ola!", resumo.mensagensRecentes().getFirst().trecho());
    }

    @Test
    void apresentaDadosDeClienteQuandoDisponiveis() {
        Cliente cliente = entityManager.find(Cliente.class, 1L);
        cliente.alterarNome("Marina");
        cliente.atualizarEmail("marina@example.com");
        entityManager.flush();
        entityManager.clear();
        var resumo = service.gerar(2L, entityManager.find(Atendente.class, 1L));
        assertEquals("Marina", resumo.cliente().nome());
        assertEquals("marina@example.com", resumo.cliente().email());
        assertTrue(resumo.dadosAusentes().isEmpty());
    }

    @Test
    void bloqueiaOutraEmpresaAtendenteInativoEIdentificadoresInvalidos() {
        Atendente atendente = entityManager.find(Atendente.class, 1L);
        Atendente externo = entityManager.find(Atendente.class, 3L);
        assertThrows(IllegalArgumentException.class, () -> service.gerar(2L, externo));
        assertThrows(IllegalArgumentException.class, () -> service.gerar(999L, atendente));
        assertThrows(IllegalArgumentException.class, () -> service.gerar(0L, atendente));
        assertThrows(IllegalArgumentException.class, () -> service.gerar(null, atendente));
        assertThrows(NullPointerException.class, () -> service.gerar(1L, null));
        atendente.desativar(entityManager.find(Atendente.class, 2L));
        assertThrows(SecurityException.class, () -> service.gerar(1L, atendente));
    }
}
