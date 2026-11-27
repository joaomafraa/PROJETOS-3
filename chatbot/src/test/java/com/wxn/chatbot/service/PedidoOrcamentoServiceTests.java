package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.PedidoOrcamentoRegistrado;
import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.PedidoOrcamentoRepository;

import jakarta.persistence.EntityManager;

// HU06.1: registro do pedido de orcamento (necessidade + contato) pela camada de negocio.
// Sem @Transactional na classe: cada operacao confirma no banco, como no uso real.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pedido-orcamento-servico;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexus.telegram.polling.enabled=false"
})
@ActiveProfiles("test")
@RecordApplicationEvents
class PedidoOrcamentoServiceTests {
    private static final String NECESSIDADE = "Preciso de um orçamento para 20 licenças do sistema.";

    @Autowired private PedidoOrcamentoService orcamentos;
    @Autowired private AbrirChamadoService chamados;
    @Autowired private ConsultaTicketsService consulta;
    @Autowired private PedidoOrcamentoRepository contatos;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ApplicationEvents eventos;

    private TransactionTemplate transacao;

    // Empresa 1: conversa 1 (Marina, so telefone), 2 (Carlos, telefone e e-mail) e 4 (sem nenhum contato).
    // Empresa 2: conversa 3 (cliente externo).
    @BeforeEach
    void preparar() {
        transacao = new TransactionTemplate(manager);
        transacao.executeWithoutResult(status -> {
            for (String tabela : List.of("telegram_pedido_orcamento", "notificacoes_chamados", "pedidos_orcamento",
                    "operacoes_solicitacao", "preferencias_agendamento", "tickets", "mensagens", "conversas",
                    "clientes", "chatbots", "atendentes", "empresas")) {
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
            carlos.atualizarEmail("carlos@example.com");
            carlos.vincularEmpresa(empresa);
            Cliente externo = new Cliente(3L, "+5511999990003");
            externo.vincularEmpresa(outra);
            Cliente semContato = Cliente.semTelefone(4L);
            semContato.vincularEmpresa(empresa);
            entityManager.persist(marina);
            entityManager.persist(carlos);
            entityManager.persist(externo);
            entityManager.persist(semContato);
            entityManager.persist(new Conversa(1L, marina, empresa, bot));
            entityManager.persist(new Conversa(2L, carlos, empresa, bot));
            entityManager.persist(new Conversa(3L, externo, outra, botExterno));
            entityManager.persist(new Conversa(4L, semContato, empresa, bot));
        });
        eventos.clear();
    }

    private long quantidadeTickets() {
        return transacao.execute(status -> entityManager.createQuery("select count(t) from Ticket t", Long.class)
                .getSingleResult());
    }

    private long quantidadePedidos() {
        return transacao.execute(status -> entityManager.createQuery("select count(p) from PedidoOrcamento p", Long.class)
                .getSingleResult());
    }

    private long eventosPublicados() {
        return eventos.stream(SolicitacaoRegistradaEvento.class).count();
    }

    private Atendente atendente(long id) {
        return transacao.execute(status -> entityManager.find(Atendente.class, id));
    }

    // ---------- Registro ----------

    @Test
    void registraOrcamentoComNecessidadeEContatoDoCadastro() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(1L, 1L, "  " + NECESSIDADE + "  ", null, "telegram:9001");

        assertNotNull(pedido.id());
        assertTrue(pedido.protocolo().matches("PD-[0-9]{20}"));
        assertEquals(Ticket.TipoSolicitacao.ORCAMENTO, pedido.tipo());
        assertEquals(Ticket.StatusTicket.PENDENTE, pedido.status());
        assertEquals(NECESSIDADE, pedido.necessidade());
        assertEquals("Telefone: +5581999990001", pedido.contato());
        assertEquals(1L, pedido.empresaId());
        assertEquals(1L, pedido.clienteId());
        assertEquals(1L, pedido.conversaId());
        assertNotNull(pedido.dataAbertura());
        assertTrue(pedido.novo());

        transacao.executeWithoutResult(status -> {
            Ticket salvo = entityManager.find(Ticket.class, pedido.id());
            assertEquals(pedido.protocolo(), salvo.getProtocolo());
            assertEquals(NECESSIDADE, salvo.getDescricao());
            assertEquals(1L, salvo.getEmpresa().getId());
            assertEquals(1L, salvo.getConversaOrigem().getId());
            assertEquals("Telefone: +5581999990001",
                    contatos.findByTicketIdAndEmpresaId(pedido.id(), 1L).orElseThrow().getContato());
        });
        assertEquals(1, eventosPublicados(), "A equipe deve ser avisada uma vez.");
    }

    @Test
    void usaTelefoneEEmailQuandoOCadastroTemOsDois() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(1L, 2L, NECESSIDADE, null, "telegram:9002");

        assertEquals("Telefone: +5581999990002; E-mail: carlos@example.com", pedido.contato());
    }

    @Test
    void naoCriaLeadPresumido() {
        orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9003");

        long leads = transacao.execute(status -> entityManager.createQuery("select count(l) from Lead l", Long.class)
                .getSingleResult());
        assertEquals(0, leads);
    }

    // ---------- Contato ----------

    @Test
    void semContatoNoCadastroPedeOContatoENadaEhCriado() {
        assertTrue(orcamentos.contatoCadastrado(1L, 1L).isPresent());
        assertTrue(orcamentos.contatoCadastrado(1L, 4L).isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> orcamentos.registrar(1L, 4L, NECESSIDADE, null, "telegram:9010"));
        assertThrows(IllegalArgumentException.class,
                () -> orcamentos.registrar(1L, 4L, NECESSIDADE, "   ", "telegram:9010"));
        assertEquals(0, quantidadeTickets());
        assertEquals(0, quantidadePedidos());
        assertEquals(0, eventosPublicados());
    }

    @Test
    void contatoInformadoPeloClienteEhNormalizadoEGuardado() {
        PedidoOrcamentoRegistrado porEmail = orcamentos.registrar(1L, 4L, NECESSIDADE, "  Fulano@Example.COM ", "telegram:9011");
        PedidoOrcamentoRegistrado porTelefone = orcamentos.registrar(1L, 4L, NECESSIDADE, "(81) 99999-0004", "telegram:9012");

        assertEquals("E-mail: fulano@example.com", porEmail.contato());
        assertEquals("Telefone: 81999990004", porTelefone.contato());
    }

    @Test
    void contatoInformadoTemPrecedenciaSobreOCadastro() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(1L, 1L, NECESSIDADE, "comercial@example.com", "telegram:9013");

        assertEquals("E-mail: comercial@example.com", pedido.contato());
    }

    @Test
    void contatoInformadoInvalidoNaoCriaPedido() {
        for (String invalido : List.of("abc", "a@b", "fulano@", "@example.com", "12345", "+55 (81) abc")) {
            assertThrows(IllegalArgumentException.class,
                    () -> orcamentos.registrar(1L, 4L, NECESSIDADE, invalido, "telegram:9014"), invalido);
        }
        assertEquals(0, quantidadeTickets());
        assertEquals(0, quantidadePedidos());
    }

    // ---------- Dados invalidos ----------

    @Test
    void necessidadeInvalidaOuChaveInvalidaNaoCriamPedido() {
        for (String necessidade : new String[] {null, "", "   ", "curto", "x".repeat(2001)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> orcamentos.registrar(1L, 1L, necessidade, null, "telegram:9020"));
        }
        for (String chave : new String[] {null, "", "  ", "k".repeat(121)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> orcamentos.registrar(1L, 1L, NECESSIDADE, null, chave));
        }
        assertEquals(0, quantidadeTickets());
        assertEquals(0, quantidadePedidos());
        assertEquals(0, eventosPublicados());
    }

    @Test
    void aceitaNecessidadeNosLimites() {
        assertDoesNotThrow(() -> orcamentos.registrar(1L, 1L, "x".repeat(10), null, "telegram:9021"));
        assertDoesNotThrow(() -> orcamentos.registrar(1L, 1L, "x".repeat(2000), null, "telegram:9022"));
    }

    // ---------- Repeticao e idempotencia ----------

    @Test
    void mesmaOperacaoDevolveOMesmoPedidoSemDuplicar() {
        PedidoOrcamentoRegistrado primeiro = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9030");
        PedidoOrcamentoRegistrado repetido = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9030");

        assertTrue(primeiro.novo());
        assertFalse(repetido.novo(), "A repetição deve ser reconhecida para não confirmar de novo.");
        assertEquals(primeiro.id(), repetido.id());
        assertEquals(primeiro.protocolo(), repetido.protocolo());
        assertEquals(primeiro.contato(), repetido.contato());
        assertEquals(1, quantidadeTickets());
        assertEquals(1, quantidadePedidos());
        assertEquals(1, eventosPublicados(), "A repetição não deve avisar a equipe de novo.");
    }

    @Test
    void novaOperacaoDoMesmoClienteCriaOutroPedido() {
        PedidoOrcamentoRegistrado primeiro = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9031");
        PedidoOrcamentoRegistrado segundo = orcamentos.registrar(1L, 1L, "Quero orçar também a implantação.", null, "telegram:9032");

        assertNotEquals(primeiro.id(), segundo.id());
        assertNotEquals(primeiro.protocolo(), segundo.protocolo());
        assertEquals(2, quantidadeTickets());
        assertEquals(2, quantidadePedidos());
        assertEquals(2, eventosPublicados());
    }

    @Test
    void chaveJaUsadaEmOutraConversaOuOutroTipoEhRecusada() {
        orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9033");

        assertThrows(IllegalStateException.class,
                () -> orcamentos.registrar(1L, 2L, NECESSIDADE, null, "telegram:9033"));
        assertThrows(IllegalStateException.class,
                () -> chamados.abrir(1L, 1L, "Problema com a chave repetida.", "telegram:9033"));
        assertEquals(1, quantidadeTickets());
        assertEquals(1, quantidadePedidos());
    }

    @Test
    void duasTentativasSimultaneasComAMesmaChaveCriamUmSoPedido() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Object> tentativa = () -> {
            largada.await();
            try {
                return orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9034");
            } catch (RuntimeException erro) {
                return erro; // a segunda tentativa pode ser barrada pela chave; ela e reprocessavel
            }
        };
        Future<Object> primeira = pool.submit(tentativa);
        Future<Object> segunda = pool.submit(tentativa);
        largada.countDown();
        primeira.get(30, TimeUnit.SECONDS);
        segunda.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, quantidadeTickets());
        assertEquals(1, quantidadePedidos());
        assertFalse(orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9034").novo());
        assertEquals(1, quantidadeTickets());
    }

    @Test
    void falhaDepoisDoRegistroNaMesmaTransacaoDesfazOPedido() {
        // Simula uma falha ao preparar a resposta ao cliente: o pedido nao pode ficar salvo sem confirmacao.
        assertThrows(IllegalStateException.class, () -> transacao.executeWithoutResult(status -> {
            orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9035");
            throw new IllegalStateException("falha ao preparar a resposta");
        }));
        assertEquals(0, quantidadeTickets());
        assertEquals(0, quantidadePedidos());

        PedidoOrcamentoRegistrado refeito = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9035");
        assertTrue(refeito.novo());
        assertEquals(1, quantidadeTickets());
    }

    // ---------- Isolamento entre empresas ----------

    @Test
    void conversaDeOutraEmpresaNaoRegistraPedido() {
        assertThrows(IllegalArgumentException.class,
                () -> orcamentos.registrar(1L, 3L, NECESSIDADE, "x@example.com", "telegram:9040"));
        assertThrows(IllegalArgumentException.class, () -> orcamentos.contatoCadastrado(1L, 3L));
        assertEquals(0, quantidadeTickets());
        assertEquals(0, quantidadePedidos());
    }

    @Test
    void mesmaChaveEmOutraEmpresaEOutraOperacao() {
        PedidoOrcamentoRegistrado daEmpresa = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9041");
        PedidoOrcamentoRegistrado daOutra = orcamentos.registrar(2L, 3L, "Orçamento da outra empresa.", null, "telegram:9041");

        assertNotEquals(daEmpresa.id(), daOutra.id());
        assertEquals(2L, daOutra.empresaId());
        assertEquals(3L, daOutra.clienteId());
        assertEquals("Telefone: +5511999990003", daOutra.contato());
        transacao.executeWithoutResult(status -> {
            assertTrue(contatos.findByTicketIdAndEmpresaId(daOutra.id(), 1L).isEmpty(),
                    "O contato de uma empresa não pode ser lido pela outra.");
            assertTrue(contatos.findByTicketIdAndEmpresaId(daEmpresa.id(), 2L).isEmpty());
        });
    }

    // ---------- Painel ----------

    @Test
    void pedidoApareceNaListaDaEquipeSoDaMesmaEmpresa() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9060");
        var filtro = new com.wxn.chatbot.dto.FiltroTickets(Ticket.TipoSolicitacao.ORCAMENTO, null, null, false);

        var daEquipe = consulta.listar(atendente(1L), filtro, 0, 10);
        assertEquals(1, daEquipe.totalElementos());
        assertEquals(pedido.protocolo(), daEquipe.conteudo().get(0).protocolo());
        assertEquals(0, consulta.listar(atendente(2L), filtro, 0, 10).totalElementos());
    }

    @Test
    void detalheMostraOContatoSoParaAEmpresaDoPedido() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(1L, 1L, NECESSIDADE, null, "telegram:9050");
        TicketAberto chamado = chamados.abrir(1L, 2L, "Erro ao entrar no sistema.", "telegram:9051");

        var detalhe = consulta.detalhar(pedido.id(), atendente(1L));
        assertEquals("Telefone: +5581999990001", detalhe.contatoOrcamento());
        assertEquals(NECESSIDADE, detalhe.descricao());
        assertEquals(Ticket.TipoSolicitacao.ORCAMENTO, detalhe.tipo());
        assertNull(detalhe.preferenciaHorario(), "Orçamento não tem preferência de horário.");

        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(pedido.id(), atendente(2L)));
        assertNull(consulta.detalhar(chamado.id(), atendente(1L)).contatoOrcamento(),
                "Chamado de suporte não tem contato de orçamento.");
    }
}
