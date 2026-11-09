package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.TicketAberto;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.TicketRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:abrir-chamado;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class AbrirChamadoServiceTests {

    @Autowired private EntityManager entityManager;
    @Autowired private AbrirChamadoService servico;
    @Autowired private TicketRepository tickets;

    @BeforeEach
    void preparar() {
        Empresa empresaA = new Empresa(101L, "Empresa A");
        Empresa empresaB = new Empresa(202L, "Empresa B");
        entityManager.persist(empresaA);
        entityManager.persist(empresaB);

        Cliente clienteA = new Cliente(101L, "+5581999990001");
        clienteA.vincularEmpresa(empresaA);
        Cliente clienteB = new Cliente(202L, "+5581999990002");
        clienteB.vincularEmpresa(empresaB);
        Cliente semEmpresa = new Cliente(303L, "+5581999990003");
        entityManager.persist(clienteA);
        entityManager.persist(clienteB);
        entityManager.persist(semEmpresa);

        Chatbot botA = new Chatbot(101L, "Bot A", empresaA, "Ola");
        Chatbot botB = new Chatbot(202L, "Bot B", empresaB, "Ola");
        entityManager.persist(botA);
        entityManager.persist(botB);

        entityManager.persist(new Conversa(101L, clienteA, empresaA, botA));
        entityManager.persist(new Conversa(202L, clienteB, empresaB, botB));
        entityManager.persist(new Conversa(303L, semEmpresa, empresaA, botA));
        entityManager.flush();
    }

    @Test
    void abreEPersisteChamadoComOsDadosDaConversa() {
        TicketAberto aberto = servico.abrir(101L, 101L, "Sistema indisponivel");

        assertNotNull(aberto.id());
        assertTrue(aberto.protocolo().matches("CH-[0-9]{20}"));
        assertEquals("Sistema indisponivel", aberto.descricao());
        assertEquals(Ticket.TipoSolicitacao.SUPORTE, aberto.tipo());
        assertEquals(Ticket.StatusTicket.PENDENTE, aberto.status());
        assertNotNull(aberto.dataAbertura());
        assertEquals(101L, aberto.empresaId());
        assertEquals(101L, aberto.clienteId());
        assertEquals(101L, aberto.conversaId());

        entityManager.clear();
        Ticket persistido = tickets.findByIdAndEmpresaId(aberto.id(), 101L).orElseThrow();
        assertEquals(aberto.protocolo(), persistido.getProtocolo());
        assertEquals(101L, persistido.getCliente().getId());
        assertEquals(101L, persistido.getConversaOrigem().getId());
    }

    @Test
    void geraProtocolosUnicosParaChamadosDiferentes() {
        TicketAberto primeiro = servico.abrir(101L, 101L, "Primeiro problema");
        TicketAberto segundo = servico.abrir(101L, 101L, "Segundo problema");

        assertNotEquals(primeiro.id(), segundo.id());
        assertNotEquals(primeiro.protocolo(), segundo.protocolo());
        assertTrue(tickets.existsByProtocolo(primeiro.protocolo()));
        assertTrue(tickets.existsByProtocolo(segundo.protocolo()));
    }

    @Test
    void naoAbreChamadoComConversaDeOutraEmpresa() {
        long quantidadeInicial = tickets.count();

        assertThrows(IllegalArgumentException.class,
                () -> servico.abrir(101L, 202L, "Acesso indevido"));

        assertEquals(quantidadeInicial, tickets.count());
    }

    @Test
    void rejeitaClienteSemVinculoComAEmpresaDaConversa() {
        assertThrows(IllegalStateException.class,
                () -> servico.abrir(101L, 303L, "Relacionamento invalido"));
    }

    @Test
    void rejeitaEntradasInvalidas() {
        assertThrows(IllegalArgumentException.class,
                () -> servico.abrir(null, 101L, "Problema"));
        assertThrows(IllegalArgumentException.class,
                () -> servico.abrir(101L, 0L, "Problema"));
        assertThrows(NullPointerException.class,
                () -> servico.abrir(101L, 101L, null));
        assertThrows(IllegalArgumentException.class,
                () -> servico.abrir(101L, 101L, "  "));
    }
}
