package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.PedidoOrcamentoRegistrado;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;
import com.wxn.chatbot.repository.NotificacaoChamadoRepository;
import com.wxn.chatbot.service.PedidoOrcamentoService;

import jakarta.persistence.EntityManager;

// HU06.1 + HU05.3: um pedido de orcamento realmente criado avisa os responsaveis da propria empresa.
// Com commit (as notificacoes so nascem depois dele): a classe nao e @Transactional.
//
// Empresa 71: Ana (admin, responsavel), Bruno (responsavel), Carla (responsavel, inativa), Davi (nao responsavel)
// Empresa 72: Elisa (responsavel)
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:notificacao-pedido-orcamento;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificacaoPedidoOrcamentoTests {
    private static final String NECESSIDADE = "Preciso de um orçamento para 20 licenças do sistema.";

    @Autowired private TransactionTemplate transacao;
    @Autowired private EntityManager em;
    @Autowired private PedidoOrcamentoService orcamentos;
    @Autowired private NotificacaoChamadoRepository notificacoes;

    @BeforeEach
    void preparar() {
        transacao.executeWithoutResult(status -> {
            if (em.find(Empresa.class, 71L) != null) {
                return; // banco desta classe ja preparado por um teste anterior
            }
            Empresa a = new Empresa(71L, "Empresa A");
            Empresa b = new Empresa(72L, "Empresa B");
            em.persist(a);
            em.persist(b);
            Atendente ana = atendente(701L, "Ana", "ana@orcamento-a.example", a, PerfilAcesso.ADMINISTRADOR);
            Atendente bruno = atendente(702L, "Bruno", "bruno@orcamento-a.example", a, PerfilAcesso.ATENDENTE);
            Atendente carla = atendente(703L, "Carla", "carla@orcamento-a.example", a, PerfilAcesso.ATENDENTE);
            atendente(704L, "Davi", "davi@orcamento-a.example", a, PerfilAcesso.ATENDENTE);
            Atendente elisa = atendente(711L, "Elisa", "elisa@orcamento-b.example", b, PerfilAcesso.ATENDENTE);
            em.persist(new ResponsavelNotificacao(ana, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(bruno, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(carla, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(elisa, TipoNotificacao.NOVO_TICKET));
            em.flush();
            carla.desativar(ana); // continua configurada, mas inativa
            conversa(7101L, a);
            conversa(7102L, b);
        });
    }

    private Atendente atendente(Long id, String nome, String email, Empresa empresa, PerfilAcesso perfil) {
        Atendente atendente = new Atendente(id, nome, email, empresa, perfil);
        em.persist(atendente);
        return atendente;
    }

    private void conversa(Long id, Empresa empresa) {
        Cliente cliente = new Cliente(id, "+55819998" + id);
        cliente.vincularEmpresa(empresa);
        cliente.alterarNome("Cliente " + id);
        em.persist(cliente);
        Chatbot bot = new Chatbot(id, "Bot " + id, empresa, "Olá");
        em.persist(bot);
        em.persist(new Conversa(id, cliente, empresa, bot));
    }

    private List<Long> destinatariosDo(Long ticketId) {
        return transacao.execute(status -> notificacoes.findAll().stream()
                .filter(n -> n.getTicket().getId().equals(ticketId))
                .map(n -> n.getDestinatario().getId())
                .sorted()
                .toList());
    }

    @Test
    void novoOrcamentoAvisaSoOsResponsaveisAtivosDaMesmaEmpresa() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(71L, 7101L, NECESSIDADE, null, "telegram:7001");

        // Ana e Bruno; nao Carla (inativa), nem Davi (nao responsavel), nem Elisa (outra empresa).
        assertEquals(List.of(701L, 702L), destinatariosDo(pedido.id()));
    }

    @Test
    void orcamentoDeOutraEmpresaNaoAvisaAEmpresaA() {
        PedidoOrcamentoRegistrado pedido = orcamentos.registrar(72L, 7102L, NECESSIDADE, null, "telegram:7002");

        assertEquals(List.of(711L), destinatariosDo(pedido.id()));
    }

    @Test
    void repetirAOperacaoNaoAvisaDeNovo() {
        PedidoOrcamentoRegistrado primeiro = orcamentos.registrar(71L, 7101L, NECESSIDADE, null, "telegram:7003");
        PedidoOrcamentoRegistrado repetido = orcamentos.registrar(71L, 7101L, NECESSIDADE, null, "telegram:7003");

        assertTrue(primeiro.novo());
        assertFalse(repetido.novo());
        assertEquals(List.of(701L, 702L), destinatariosDo(primeiro.id()));
    }

    @Test
    void pedidoInvalidoNaoAvisaNinguem() {
        long antes = notificacoes.count();
        try {
            orcamentos.registrar(71L, 7101L, "curto", null, "telegram:7004");
        } catch (IllegalArgumentException esperado) {
            // pedido recusado: nada foi salvo
        }

        assertEquals(antes, notificacoes.count());
    }
}
