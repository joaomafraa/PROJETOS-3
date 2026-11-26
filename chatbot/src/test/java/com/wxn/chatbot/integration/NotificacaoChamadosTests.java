package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.NotificacaoChamado;
import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;
import com.wxn.chatbot.repository.NotificacaoChamadoRepository;
import com.wxn.chatbot.repository.TicketRepository;
import com.wxn.chatbot.service.AbrirChamadoService;
import com.wxn.chatbot.service.NotificacaoChamadosOuvinte;
import com.wxn.chatbot.service.NotificacaoChamadosService;

import jakarta.persistence.EntityManager;

// HU05.3 de ponta a ponta no banco H2, COM commit (as notificacoes so nascem depois do commit).
// Por isso esta classe nao e @Transactional: os dados ficam no banco proprio desta classe.
//
// Empresa A (61): Ana (admin, responsavel), Bruno (responsavel), Carla (responsavel, inativa), Davi (nao responsavel)
// Empresa B (62): Elisa (responsavel)
// Empresa C (63): Fabio, ninguem configurado como responsavel
// Empresa D (64): Gil, vira responsavel so depois do chamado (teste do reprocessamento)
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:notificacao-chamados;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificacaoChamadosTests {

    @Autowired private TransactionTemplate transacao;
    @Autowired private EntityManager em;
    @Autowired private AbrirChamadoService abrirChamado;
    @Autowired private NotificacaoChamadosService servico;
    @Autowired private NotificacaoChamadosOuvinte ouvinte;
    @Autowired private NotificacaoChamadoRepository notificacoes;
    @Autowired private TicketRepository tickets;

    @BeforeEach
    void preparar() {
        transacao.executeWithoutResult(status -> {
            if (em.find(Empresa.class, 61L) != null) {
                return; // banco desta classe ja preparado por um teste anterior
            }
            Empresa a = empresa(61L, "Empresa A");
            Empresa b = empresa(62L, "Empresa B");
            Empresa c = empresa(63L, "Empresa C");
            Empresa d = empresa(64L, "Empresa D");

            Atendente ana = atendente(601L, "Ana", "ana@notif-a.example", a, PerfilAcesso.ADMINISTRADOR);
            Atendente bruno = atendente(602L, "Bruno", "bruno@notif-a.example", a, PerfilAcesso.ATENDENTE);
            Atendente carla = atendente(603L, "Carla", "carla@notif-a.example", a, PerfilAcesso.ATENDENTE);
            atendente(604L, "Davi", "davi@notif-a.example", a, PerfilAcesso.ATENDENTE);
            Atendente elisa = atendente(611L, "Elisa", "elisa@notif-b.example", b, PerfilAcesso.ATENDENTE);
            atendente(621L, "Fabio", "fabio@notif-c.example", c, PerfilAcesso.ATENDENTE);
            atendente(631L, "Gil", "gil@notif-d.example", d, PerfilAcesso.ATENDENTE);

            em.persist(new ResponsavelNotificacao(ana, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(bruno, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(carla, TipoNotificacao.NOVO_TICKET));
            em.persist(new ResponsavelNotificacao(elisa, TipoNotificacao.NOVO_TICKET));
            // Bruno tambem e responsavel por leads: isso nao pode virar aviso de chamado em dobro.
            em.persist(new ResponsavelNotificacao(bruno, TipoNotificacao.NOVO_LEAD));
            em.flush();
            carla.desativar(ana); // continua configurada, mas inativa

            conversa(6001L, a);
            conversa(6002L, b);
            conversa(6003L, c);
            conversa(6004L, d);
        });
    }

    private Empresa empresa(Long id, String nome) {
        Empresa e = new Empresa(id, nome);
        em.persist(e);
        return e;
    }

    private Atendente atendente(Long id, String nome, String email, Empresa empresa, PerfilAcesso perfil) {
        Atendente a = new Atendente(id, nome, email, empresa, perfil);
        em.persist(a);
        return a;
    }

    private void conversa(Long id, Empresa empresa) {
        Cliente cliente = new Cliente(id, "+55819999" + id);
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

    private Long avisoDe(Long ticketId, Long atendenteId) {
        return transacao.execute(status -> notificacoes.findAll().stream()
                .filter(n -> n.getTicket().getId().equals(ticketId) && n.getDestinatario().getId().equals(atendenteId))
                .map(NotificacaoChamado::getId)
                .findFirst().orElseThrow());
    }

    // ---------- Quem recebe ----------

    @Test
    void novoChamadoNotificaSoOsResponsaveisAtivosDaMesmaEmpresa() {
        TicketAberto chamado = abrirChamado.abrir(61L, 6001L, "Não consigo acessar o sistema");

        // Ana e Bruno; nao Carla (inativa), nem Davi (nao responsavel), nem Elisa (outra empresa).
        assertEquals(List.of(601L, 602L), destinatariosDo(chamado.id()));
    }

    @Test
    void chamadoDeOutraEmpresaNaoAvisaAEmpresaA() {
        TicketAberto chamado = abrirChamado.abrir(62L, 6002L, "Erro ao emitir boleto");
        assertEquals(List.of(611L), destinatariosDo(chamado.id()));
    }

    @Test
    void processarDeNovoNaoDuplicaAvisos() {
        TicketAberto chamado = abrirChamado.abrir(61L, 6001L, "Tela em branco");

        NotificacaoChamadosService.Resultado repetido = servico.notificarNovoChamado(chamado.id());

        assertEquals(0, repetido.criadas());
        assertEquals(2, repetido.jaExistentes());
        assertEquals(2, destinatariosDo(chamado.id()).size());
    }

    // ---------- Falta de responsavel e reprocessamento ----------

    @Test
    void semResponsavelOChamadoContinuaSalvoENinguemEhAvisado() {
        TicketAberto chamado = abrirChamado.abrir(63L, 6003L, "Dúvida sobre fatura");

        assertTrue(transacao.execute(s -> tickets.findById(chamado.id()).isPresent()));
        assertTrue(destinatariosDo(chamado.id()).isEmpty());
        assertTrue(servico.notificarNovoChamado(chamado.id()).semResponsaveis());
    }

    @Test
    void reprocessamentoAvisaQuemFoiConfiguradoDepois() {
        TicketAberto chamado = abrirChamado.abrir(64L, 6004L, "Instalação pendente");
        assertTrue(destinatariosDo(chamado.id()).isEmpty());

        transacao.executeWithoutResult(s -> em.persist(
                new ResponsavelNotificacao(em.find(Atendente.class, 631L), TipoNotificacao.NOVO_TICKET)));

        int criadas = ouvinte.reprocessarPendentes(Duration.ofHours(1));

        assertTrue(criadas >= 1);
        assertEquals(List.of(631L), destinatariosDo(chamado.id()));
        // Rodar de novo nao cria mais nada para este chamado.
        ouvinte.reprocessarPendentes(Duration.ofHours(1));
        assertEquals(List.of(631L), destinatariosDo(chamado.id()));
    }

    // ---------- Painel: cada um so ve e marca os proprios avisos ----------

    @Test
    void cadaAtendenteSoVeEMarcaOsProprios() {
        TicketAberto chamado = abrirChamado.abrir(61L, 6001L, "Impressora não conecta");
        Long avisoDoBruno = avisoDe(chamado.id(), 602L);

        assertTrue(servico.listar(602L, false).stream().anyMatch(n -> n.ticketId().equals(chamado.id())));
        assertTrue(servico.listar(604L, false).isEmpty()); // Davi
        assertTrue(servico.listar(611L, false).stream().noneMatch(n -> n.ticketId().equals(chamado.id()))); // Elisa

        // Davi e Elisa nao conseguem marcar o aviso do Bruno: responde como se nao existisse.
        assertThrows(NoSuchElementException.class, () -> servico.marcarComoLida(604L, avisoDoBruno));
        assertThrows(NoSuchElementException.class, () -> servico.marcarComoLida(611L, avisoDoBruno));

        long antes = servico.contarNaoLidas(602L);
        var lida = servico.marcarComoLida(602L, avisoDoBruno);
        assertTrue(lida.lida());
        assertEquals(antes - 1, servico.contarNaoLidas(602L));

        // O aviso da Ana para o mesmo chamado continua nao lido.
        assertTrue(servico.listar(601L, true).stream().anyMatch(n -> n.ticketId().equals(chamado.id())));
    }

    @Test
    void marcarTodasSoAfetaQuemPediu() {
        abrirChamado.abrir(61L, 6001L, "Sem internet");
        long daAnaAntes = servico.contarNaoLidas(601L);

        servico.marcarTodasComoLidas(602L);

        assertEquals(0, servico.contarNaoLidas(602L));
        assertEquals(daAnaAntes, servico.contarNaoLidas(601L));
    }

    @Test
    void atendenteInativoNaoConsultaAvisos() {
        assertThrows(SecurityException.class, () -> servico.listar(603L, false)); // Carla
    }
}
