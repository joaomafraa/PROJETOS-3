package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.ServicoContratado;
import com.wxn.chatbot.security.EnvioCodigoSmtp;
import com.wxn.chatbot.security.ValidacaoClienteService;
import com.wxn.chatbot.service.ConsultaServicosContratadosService;
import com.wxn.chatbot.service.ConsultaServicosContratadosService.Situacao;
import com.wxn.chatbot.telegram.ConsultaServicosTelegramService;
import com.wxn.chatbot.telegram.TelegramCliente;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

// HU04.1: cliente validado consulta apenas os proprios planos e servicos ficticios.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:servicos-contratados;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ConsultaServicosContratadosTests {

    private static final long EMPRESA = 85001L;
    private static final long OUTRA_EMPRESA = 85002L;
    private static final long CONVERSA_MARINA = 85001L;
    private static final long CONVERSA_PAULO = 85002L;
    private static final long CONVERSA_OUTRA_EMPRESA = 85003L;

    @Autowired EntityManager em;
    @Autowired ConsultaServicosContratadosService consulta;
    @Autowired ConsultaServicosTelegramService telegramPlanos;
    @Autowired ValidacaoClienteService validacao;
    @MockitoBean EnvioCodigoSmtp email;
    @MockitoBean TelegramCliente telegram;

    private Cliente marina;
    private Cliente paulo;
    private Cliente daOutraEmpresa;

    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(EMPRESA, "Empresa Exemplo");
        Empresa outra = new Empresa(OUTRA_EMPRESA, "Empresa Alfa");
        em.persist(empresa);
        em.persist(outra);
        marina = cliente(85001L, "81970000001", "marina@example.test", empresa);
        paulo = cliente(85002L, "81970000002", "paulo@example.test", empresa);
        daOutraEmpresa = cliente(85003L, "81970000003", "alfa@example.test", outra);
        Chatbot bot = new Chatbot(85001L, "Bot", empresa, "Olá");
        Chatbot botAlfa = new Chatbot(85002L, "Bot Alfa", outra, "Olá");
        em.persist(bot);
        em.persist(botAlfa);
        em.persist(new Conversa(CONVERSA_MARINA, marina, empresa, bot));
        em.persist(new Conversa(CONVERSA_PAULO, paulo, empresa, bot));
        em.persist(new Conversa(CONVERSA_OUTRA_EMPRESA, daOutraEmpresa, outra, botAlfa));

        em.persist(new ServicoContratado(marina, ServicoContratado.Tipo.SERVICO, "Suporte prioritário", null,
                ServicoContratado.Situacao.ENCERRADO, LocalDate.of(2025, 1, 10), LocalDate.of(2025, 12, 31)));
        em.persist(new ServicoContratado(marina, ServicoContratado.Tipo.PLANO, "Plano Empresarial",
                "Portal corporativo para até 50 usuários.", ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 3, 1), null));
        em.persist(new ServicoContratado(daOutraEmpresa, ServicoContratado.Tipo.PLANO, "Plano Alfa Premium", null,
                ServicoContratado.Situacao.ATIVO, LocalDate.of(2026, 1, 1), null));
        em.flush();
        for (Cliente cliente : List.of(marina, paulo, daOutraEmpresa)) {
            em.createNativeQuery("insert into emails_confiaveis_cliente (cliente_id, empresa_id, email) values (?, ?, ?)")
                    .setParameter(1, cliente.getId()).setParameter(2, cliente.getEmpresa().getId())
                    .setParameter(3, cliente.getEmail()).executeUpdate();
        }
    }

    @Test
    void semValidacaoNaoConsulta() {
        var resultado = consulta.consultar(EMPRESA, CONVERSA_MARINA);
        assertEquals(Situacao.VALIDACAO_NECESSARIA, resultado.situacao());
        assertTrue(resultado.itens().isEmpty());
        assertEquals("Para proteger seus dados, preciso confirmar sua identidade antes de mostrar seus planos e serviços. "
                        + "Toque em /validar e digite os seis números que receber por e-mail.",
                telegramPlanos.responder(EMPRESA, CONVERSA_MARINA));
    }

    @Test
    void clienteValidadoVeSoOsProprios() {
        validar(EMPRESA, CONVERSA_MARINA, "marina@example.test");

        var resultado = consulta.consultar(EMPRESA, CONVERSA_MARINA);

        assertEquals(Situacao.ENCONTRADO, resultado.situacao());
        assertEquals(List.of("Plano Empresarial", "Suporte prioritário"),
                resultado.itens().stream().map(ConsultaServicosContratadosService.Item::nome).toList());
        assertTrue(resultado.itens().stream().noneMatch(i -> i.nome().equals("Plano Alfa Premium")));
    }

    @Test
    void telegramApresentaComoDemonstracao() {
        validar(EMPRESA, CONVERSA_MARINA, "marina@example.test");

        String texto = telegramPlanos.responder(EMPRESA, CONVERSA_MARINA);

        assertEquals("""
                Seus planos e serviços (dados de demonstração):

                • Plano Empresarial (plano) — ativo
                  Portal corporativo para até 50 usuários.
                  Vigência: desde 01/03/2026

                • Suporte prioritário (serviço) — encerrado
                  Vigência: 10/01/2025 a 31/12/2025""", texto);
    }

    @Test
    void outroClienteDaMesmaEmpresaNaoVeOsDadosDaMarina() {
        validar(EMPRESA, CONVERSA_PAULO, "paulo@example.test");

        var resultado = consulta.consultar(EMPRESA, CONVERSA_PAULO);

        assertEquals(Situacao.SEM_DADOS, resultado.situacao());
        assertTrue(resultado.itens().isEmpty());
        assertTrue(telegramPlanos.responder(EMPRESA, CONVERSA_PAULO).startsWith("Não há plano ou serviço"));
    }

    @Test
    void validacaoDeUmaConversaNaoLiberaOutra() {
        validar(EMPRESA, CONVERSA_PAULO, "paulo@example.test");
        assertEquals(Situacao.VALIDACAO_NECESSARIA, consulta.consultar(EMPRESA, CONVERSA_MARINA).situacao());
    }

    @Test
    void conversaDeOutraEmpresaNaoEhAceita() {
        validar(OUTRA_EMPRESA, CONVERSA_OUTRA_EMPRESA, "alfa@example.test");

        // Empresa do canal (EMPRESA) com conversa forjada de outra empresa.
        var forjada = consulta.consultar(EMPRESA, CONVERSA_OUTRA_EMPRESA);
        assertNotEquals(Situacao.ENCONTRADO, forjada.situacao());
        assertTrue(forjada.itens().isEmpty());

        // Na propria empresa, o cliente Alfa ve apenas o dele.
        var propria = consulta.consultar(OUTRA_EMPRESA, CONVERSA_OUTRA_EMPRESA);
        assertEquals(List.of("Plano Alfa Premium"),
                propria.itens().stream().map(ConsultaServicosContratadosService.Item::nome).toList());
    }

    @Test
    void bancoRecusaRegistroMarcadoComoReal() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery("""
                insert into servicos_contratados (empresa_id, cliente_id, tipo, nome, situacao, inicio, demonstracao)
                values (?, ?, 'PLANO', 'Real', 'ATIVO', DATE '2026-01-01', FALSE)""")
                .setParameter(1, EMPRESA).setParameter(2, marina.getId()).executeUpdate());
    }

    private Cliente cliente(long id, String telefone, String email, Empresa empresa) {
        Cliente cliente = new Cliente(id, telefone);
        cliente.vincularEmpresa(empresa);
        cliente.atualizarEmail(email);
        em.persist(cliente);
        return cliente;
    }

    private void validar(long empresaId, long conversaId, String emailConfiavel) {
        validacao.solicitar(empresaId, conversaId);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(emailConfiavel), codigo.capture());
        assertTrue(validacao.confirmar(empresaId, conversaId, codigo.getValue()));
    }
}
