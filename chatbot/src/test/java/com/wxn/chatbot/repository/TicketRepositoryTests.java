package com.wxn.chatbot.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.wxn.chatbot.model.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryTests {
    @Autowired private EntityManager entityManager;
    @Autowired private TicketRepository tickets;

    private Ticket preparar() {
        Empresa empresa = new Empresa(101L, "WXN");
        Empresa outra = new Empresa(102L, "Outra empresa");
        Cliente cliente = new Cliente(101L, "+5581999999999");
        cliente.vincularEmpresa(empresa);
        Chatbot bot = new Chatbot(101L, "Assistente", empresa, "Ola");
        Atendente ana = new Atendente(101L, "Ana", "ana@example.com", empresa,
                Atendente.PerfilAcesso.ATENDENTE);
        Atendente admin = new Atendente(102L, "Admin", "admin@example.com", empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente externo = new Atendente(103L, "Externo", "externo@example.com", outra,
                Atendente.PerfilAcesso.ATENDENTE);
        entityManager.persist(empresa);
        entityManager.persist(outra);
        entityManager.persist(cliente);
        entityManager.persist(bot);
        entityManager.persist(ana);
        entityManager.persist(admin);
        entityManager.persist(externo);
        Conversa conversa = new Conversa(101L, cliente, empresa, bot);
        entityManager.persist(conversa);
        return new Ticket(101L, "WXN-101", "Solicitar suporte", Ticket.TipoSolicitacao.SUPORTE, conversa);
    }

    @Test
    void gravaRecarregaEAtualizaCicloDeVidaSemEncerrarConversa() {
        Ticket ticket = preparar();
        tickets.saveAndFlush(ticket);
        entityManager.clear();

        Ticket salvo = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertEquals("WXN-101", salvo.getProtocolo());
        assertEquals("Solicitar suporte", salvo.getDescricao());
        assertEquals(Ticket.TipoSolicitacao.SUPORTE, salvo.getTipo());
        assertNotNull(salvo.getDataAbertura());
        assertEquals(101L, salvo.getCliente().getId());
        assertEquals(101L, salvo.getConversaOrigem().getId());
        assertEquals(Ticket.StatusTicket.PENDENTE, salvo.getStatus());
        assertNull(salvo.getResponsavel());
        assertNull(salvo.getDataConclusao());
        assertThrows(IllegalStateException.class, salvo::iniciarAtendimento);
        assertThrows(IllegalStateException.class, salvo::concluir);
        salvo.atribuirResponsavel(entityManager.find(Atendente.class, 101L));
        salvo.iniciarAtendimento();
        tickets.flush();
        entityManager.clear();

        Ticket emAtendimento = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertEquals(Ticket.StatusTicket.EM_ATENDIMENTO, emAtendimento.getStatus());
        assertEquals("Ana", emAtendimento.getResponsavel().getNome());
        emAtendimento.concluir();
        tickets.flush();
        entityManager.clear();

        Ticket concluido = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertEquals(Ticket.StatusTicket.CONCLUIDO, concluido.getStatus());
        assertNotNull(concluido.getDataConclusao());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, concluido.getConversaOrigem().getStatus());
        assertThrows(IllegalStateException.class, concluido::iniciarAtendimento);
        assertThrows(IllegalStateException.class, concluido::concluir);
        assertThrows(IllegalStateException.class,
                () -> concluido.atribuirResponsavel(entityManager.find(Atendente.class, 101L)));
    }

    @Test
    void preservaEmpresaHistoricaEFiltraConsultasPorEmpresa() {
        Ticket ticket = preparar();
        tickets.saveAndFlush(ticket);
        ticket.getCliente().trocarEmpresa(entityManager.find(Empresa.class, 102L),
                entityManager.find(Atendente.class, 102L));
        tickets.flush();
        entityManager.clear();

        Ticket salvo = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertEquals(101L, salvo.getEmpresa().getId());
        assertEquals(102L, salvo.getCliente().getEmpresa().getId());
        assertTrue(tickets.findByIdAndEmpresaId(101L, 102L).isEmpty());
        assertTrue(tickets.findByEmpresaIdOrderByDataAberturaAscIdAsc(102L).isEmpty());
        assertEquals(1, tickets.findByEmpresaIdOrderByDataAberturaAscIdAsc(101L).size());
    }

    @Test
    void rejeitaResponsavelExternoOuInativoAposRecarga() {
        tickets.saveAndFlush(preparar());
        entityManager.clear();
        Ticket salvo = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> salvo.atribuirResponsavel(entityManager.find(Atendente.class, 103L)));
        Atendente ana = entityManager.find(Atendente.class, 101L);
        salvo.atribuirResponsavel(ana);
        ana.desativar(entityManager.find(Atendente.class, 102L));
        tickets.flush();
        entityManager.clear();
        Ticket recarregado = tickets.findByIdAndEmpresaId(101L, 101L).orElseThrow();
        assertThrows(IllegalStateException.class, recarregado::iniciarAtendimento);
        assertThrows(IllegalStateException.class,
                () -> recarregado.atribuirResponsavel(entityManager.find(Atendente.class, 101L)));
    }
}
