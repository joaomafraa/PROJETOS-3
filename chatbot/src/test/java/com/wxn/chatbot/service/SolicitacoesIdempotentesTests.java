package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.PedidoAgendamentoRegistrado;
import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.model.Ticket.TipoSolicitacao;

// HU05.1, HU05.2 e HU06.2: registro idempotente de chamados e pedidos de agendamento.
// Sem @Transactional na classe: cada operacao confirma no banco, como no uso real.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:solicitacoes-idempotentes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexus.telegram.polling.enabled=false"
})
@ActiveProfiles("test")
@RecordApplicationEvents
class SolicitacoesIdempotentesTests {
    @Autowired private AbrirChamadoService chamados;
    @Autowired private PedidoAgendamentoService pedidos;
    @Autowired private ConsultaTicketsService consulta;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ApplicationEvents eventos;

    private TransactionTemplate transacao;

    // Empresa 1: conversas 1 (Marina) e 2 (Carlos). Empresa 2: conversa 3.
    @BeforeEach
    void preparar() {
        transacao = new TransactionTemplate(manager);
        transacao.executeWithoutResult(status -> {
            for (String tabela : List.of("notificacoes_chamados", "operacoes_solicitacao", "preferencias_agendamento",
                    "tickets", "mensagens", "conversas", "clientes", "chatbots", "atendentes", "empresas")) {
                entityManager.createNativeQuery("delete from " + tabela).executeUpdate();
            }
            Empresa empresa = new Empresa(1L, "NEXUS");
            Empresa outra = new Empresa(2L, "Outra");
            entityManager.persist(empresa);
            entityManager.persist(outra);
            entityManager.persist(new Atendente(1L, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE));
            entityManager.persist(new Atendente(2L, "Externo", "externo@example.com", outra, Atendente.PerfilAcesso.ATENDENTE));
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
            entityManager.persist(new Conversa(1L, marina, empresa, bot));
            entityManager.persist(new Conversa(2L, carlos, empresa, bot));
            entityManager.persist(new Conversa(3L, externo, outra, botExterno));
        });
        eventos.clear();
    }

    private long quantidadeTickets() {
        return transacao.execute(status -> entityManager.createQuery("select count(t) from Ticket t", Long.class)
                .getSingleResult());
    }

    private long eventosPublicados() {
        return eventos.stream(SolicitacaoRegistradaEvento.class).count();
    }

    private Atendente atendente(long id) {
        return transacao.execute(status -> entityManager.find(Atendente.class, id));
    }

    // ---------- Chamados de suporte (HU05.1 / HU05.2) ----------

    @Test
    void mesmaOperacaoDevolveOMesmoChamadoSemDuplicar() {
        TicketAberto primeiro = chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");
        TicketAberto repetido = chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");

        assertEquals(primeiro.id(), repetido.id());
        assertEquals(primeiro.protocolo(), repetido.protocolo());
        assertTrue(primeiro.protocolo().matches("CH-[0-9]{20}"));
        assertEquals(1, quantidadeTickets());
        assertEquals(1, eventosPublicados(), "A repetição não deve avisar os responsáveis de novo.");
    }

    @Test
    void novaOperacaoDoMesmoClienteCriaOutroChamado() {
        TicketAberto primeiro = chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");
        TicketAberto segundo = chamados.abrir(1L, 1L, "Agora o relatório não abre.", "telegram:5002");

        assertNotEquals(primeiro.protocolo(), segundo.protocolo());
        assertEquals(2, quantidadeTickets());
        assertEquals(2, eventosPublicados());
    }

    @Test
    void chaveJaUsadaEmOutraConversaOuOutroTipoEhRecusada() {
        chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");

        assertThrows(IllegalStateException.class,
                () -> chamados.abrir(1L, 2L, "Outro cliente, mesma chave.", "telegram:5001"));
        assertThrows(IllegalStateException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "Quero uma reunião sobre o contrato.", "terça à tarde", "telegram:5001"));
        assertEquals(1, quantidadeTickets());
    }

    @Test
    void mesmaChaveEmOutraEmpresaNaoReaproveitaChamadoAlheio() {
        TicketAberto daEmpresa = chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");
        TicketAberto daOutra = chamados.abrir(2L, 3L, "Problema na outra empresa.", "telegram:5001");

        assertNotEquals(daEmpresa.id(), daOutra.id());
        assertEquals(2L, daOutra.empresaId());
        assertEquals(3L, daOutra.clienteId());
    }

    @Test
    void conversaDeOutraEmpresaNaoAbreChamado() {
        assertThrows(IllegalArgumentException.class,
                () -> chamados.abrir(1L, 3L, "Tentativa com conversa alheia.", "telegram:7001"));
        assertEquals(0, quantidadeTickets());
    }

    @Test
    void chaveInvalidaEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> chamados.abrir(1L, 1L, "Descrição válida.", " "));
        assertThrows(IllegalArgumentException.class, () -> chamados.abrir(1L, 1L, "Descrição válida.", "x".repeat(121)));
        assertEquals(0, quantidadeTickets());
    }

    @Test
    void semChaveContinuaFuncionandoComoAntes() {
        chamados.abrir(1L, 1L, "Primeiro sem chave.");
        chamados.abrir(1L, 1L, "Segundo sem chave.");
        assertEquals(2, quantidadeTickets());
    }

    @Test
    void duasTentativasSimultaneasComAMesmaChaveCriamUmSoChamado() throws Exception {
        final int rodadas = 10;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 1; rodada <= rodadas; rodada++) {
                String chave = "telegram:900" + rodada;
                CountDownLatch largada = new CountDownLatch(1);
                Callable<String> tentativa = () -> {
                    largada.await();
                    try {
                        return chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", chave).protocolo();
                    } catch (RuntimeException erro) {
                        return null; // a segunda transacao pode falhar na chave da operacao; nada fica salvo
                    }
                };
                List<Future<String>> resultados = new ArrayList<>();
                resultados.add(executor.submit(tentativa));
                resultados.add(executor.submit(tentativa));
                largada.countDown();
                List<String> protocolos = new ArrayList<>();
                for (Future<String> resultado : resultados) {
                    String protocolo = resultado.get();
                    if (protocolo != null) protocolos.add(protocolo);
                }
                assertFalse(protocolos.isEmpty(), "Rodada " + rodada + ": nenhuma tentativa registrou o chamado.");
                assertEquals(1, protocolos.stream().distinct().count(),
                        "Rodada " + rodada + ": as duas tentativas devem ver o mesmo protocolo.");
                assertEquals(rodada, quantidadeTickets(), "Rodada " + rodada + ": chamado duplicado.");
                // Reprocessar depois devolve o chamado que ficou salvo.
                assertEquals(protocolos.getFirst(),
                        chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", chave).protocolo());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(rodadas, quantidadeTickets());
    }

    // ---------- Pedidos de demonstração, reunião e visita (HU06.2) ----------

    @Test
    void registraOsTresTiposComPreferenciaEDadosDaConversa() {
        PedidoAgendamentoRegistrado demo = pedidos.registrar(1L, 1L, TipoSolicitacao.DEMONSTRACAO,
                "Quero conhecer o módulo financeiro.", "  terça   à tarde ", "telegram:6001");
        PedidoAgendamentoRegistrado reuniao = pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "Reunião para revisar o contrato.", "qualquer manhã da próxima semana", "telegram:6002");
        PedidoAgendamentoRegistrado visita = pedidos.registrar(1L, 2L, TipoSolicitacao.VISITA_TECNICA,
                "Instalação de dois terminais na loja.", "sexta, depois das 14h", "telegram:6003");

        assertEquals(TipoSolicitacao.DEMONSTRACAO, demo.tipo());
        assertEquals(TipoSolicitacao.REUNIAO, reuniao.tipo());
        assertEquals(TipoSolicitacao.VISITA_TECNICA, visita.tipo());
        assertTrue(demo.protocolo().matches("PD-[0-9]{20}"));
        assertEquals(Ticket.StatusTicket.PENDENTE, demo.status());
        assertEquals("terça à tarde", demo.preferenciaHorario(), "Espaços extras são removidos; o texto é preservado.");
        assertEquals("Quero conhecer o módulo financeiro.", demo.necessidade());
        assertTrue(demo.novo());
        assertTrue(demo.horarioAConfirmar());
        assertEquals(1L, demo.empresaId());
        assertEquals(1L, demo.clienteId());
        assertEquals(1L, demo.conversaId());
        assertEquals(2L, visita.clienteId());
        assertEquals(3, quantidadeTickets());
        assertEquals(3, eventosPublicados());
    }

    @Test
    void pedidoRepetidoDevolveOMesmoPedidoEPreferenciaOriginal() {
        PedidoAgendamentoRegistrado primeiro = pedidos.registrar(1L, 1L, TipoSolicitacao.DEMONSTRACAO,
                "Quero conhecer o módulo financeiro.", "terça à tarde", "telegram:6001");
        PedidoAgendamentoRegistrado repetido = pedidos.registrar(1L, 1L, TipoSolicitacao.DEMONSTRACAO,
                "Quero conhecer o módulo financeiro.", "terça à tarde", "telegram:6001");

        assertEquals(primeiro.protocolo(), repetido.protocolo());
        assertEquals("terça à tarde", repetido.preferenciaHorario());
        assertFalse(repetido.novo(), "A confirmação pode usar isso para não enviar de novo.");
        assertEquals(1, quantidadeTickets());
        assertEquals(1, eventosPublicados());
    }

    @Test
    void preferenciaAparecePersistidaNosDetalhesSoParaAMesmaEmpresa() {
        PedidoAgendamentoRegistrado visita = pedidos.registrar(1L, 1L, TipoSolicitacao.VISITA_TECNICA,
                "Instalação de dois terminais na loja.", "sexta, depois das 14h", "telegram:6003");

        var detalhe = consulta.detalhar(visita.id(), atendente(1L));
        assertEquals("sexta, depois das 14h", detalhe.preferenciaHorario());
        assertEquals(TipoSolicitacao.VISITA_TECNICA, detalhe.tipo());
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(visita.id(), atendente(2L)));

        TicketAberto chamado = chamados.abrir(1L, 1L, "O sistema não emite notas fiscais.", "telegram:5001");
        assertNull(consulta.detalhar(chamado.id(), atendente(1L)).preferenciaHorario(),
                "Chamado de suporte não tem preferência de horário.");
    }

    @Test
    void dadosInvalidosNaoCriamPedido() {
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.SUPORTE,
                "Isto é suporte, não agendamento.", "terça", "telegram:1"));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.ORCAMENTO,
                "Orçamento é outra história.", "terça", "telegram:2"));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "curta", "terça à tarde", "telegram:3"));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "Reunião para revisar o contrato.", " a ", "telegram:4"));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "Reunião para revisar o contrato.", "x".repeat(301), "telegram:5"));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 1L, TipoSolicitacao.REUNIAO,
                "Reunião para revisar o contrato.", "terça à tarde", null));
        assertThrows(IllegalArgumentException.class, () -> pedidos.registrar(1L, 3L, TipoSolicitacao.REUNIAO,
                "Conversa de outra empresa.", "terça à tarde", "telegram:6"));
        assertEquals(0, quantidadeTickets());
        assertEquals(0, eventosPublicados(), "Nada registrado, ninguém é avisado.");
    }
}
