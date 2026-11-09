package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.TicketRepository;
import com.wxn.chatbot.service.AssumirTicketService;
import com.wxn.chatbot.service.AtendimentoHumanoService;
import com.wxn.chatbot.service.ConsultaTicketsService;
import com.wxn.chatbot.service.FilaConversasService;
import com.wxn.chatbot.service.HistoricoConversasService;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:isolamento-empresa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class IsolamentoEmpresaTests {

    @Autowired private EntityManager entityManager;
    @Autowired private FilaConversasService fila;
    @Autowired private HistoricoConversasService historico;
    @Autowired private ConsultaTicketsService consultaTickets;
    @Autowired private AtendimentoHumanoService atendimentoHumano;
    @Autowired private AssumirTicketService assumirTicket;
    @Autowired private ConversaRepository conversas;
    @Autowired private TicketRepository tickets;

    @BeforeEach
    void preparar() {
        Empresa empresaA = new Empresa(11L, "Empresa A");
        Empresa empresaB = new Empresa(22L, "Empresa B");
        entityManager.persist(empresaA);
        entityManager.persist(empresaB);

        Atendente atendenteA = new Atendente(11L, "Ana", "ana@a.example", empresaA,
                Atendente.PerfilAcesso.ATENDENTE);
        Atendente atendenteB = new Atendente(22L, "Beto", "beto@b.example", empresaB,
                Atendente.PerfilAcesso.ATENDENTE);
        entityManager.persist(atendenteA);
        entityManager.persist(atendenteB);

        Cliente clienteA = new Cliente(11L, "+5581999990011");
        clienteA.vincularEmpresa(empresaA);
        Cliente clienteB = new Cliente(22L, "+5581999990022");
        clienteB.vincularEmpresa(empresaB);
        entityManager.persist(clienteA);
        entityManager.persist(clienteB);

        Chatbot botA = new Chatbot(11L, "Bot A", empresaA, "Ola");
        Chatbot botB = new Chatbot(22L, "Bot B", empresaB, "Ola");
        entityManager.persist(botA);
        entityManager.persist(botB);

        Conversa conversaA = new Conversa(11L, clienteA, empresaA, botA);
        conversaA.adicionarMensagem(1L, "Mensagem A", Mensagem.TipoRemetente.CLIENTE);
        conversaA.solicitarAtendimentoHumano();
        Conversa conversaB = new Conversa(22L, clienteB, empresaB, botB);
        conversaB.adicionarMensagem(1L, "Mensagem B", Mensagem.TipoRemetente.CLIENTE);
        conversaB.solicitarAtendimentoHumano();
        entityManager.persist(conversaA);
        entityManager.persist(conversaB);

        entityManager.persist(new Ticket(11L, "A-11", "Chamado A",
                Ticket.TipoSolicitacao.SUPORTE, conversaA));
        entityManager.persist(new Ticket(22L, "B-22", "Chamado B",
                Ticket.TipoSolicitacao.SUPORTE, conversaB));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void consultasDaEmpresaANaoExibemDadosDaEmpresaB() {
        Atendente atendenteA = entityManager.find(Atendente.class, 11L);

        assertEquals(1, fila.visualizarFila(atendenteA).size());
        assertEquals(11L, fila.visualizarFila(atendenteA).getFirst().id());
        assertEquals(1, consultaTickets.listar(atendenteA, FiltroTickets.nenhum(), 0, 20)
                .totalElementos());
        assertEquals(11L, consultaTickets.listar(atendenteA, FiltroTickets.nenhum(), 0, 20)
                .conteudo().getFirst().id());
        assertThrows(IllegalArgumentException.class,
                () -> historico.visualizarHistorico(22L, atendenteA));
        assertThrows(IllegalArgumentException.class,
                () -> consultaTickets.detalhar(22L, atendenteA));
    }

    @Test
    void atendenteDaEmpresaANaoModificaDadosDaEmpresaB() {
        assertThrows(IllegalArgumentException.class,
                () -> atendimentoHumano.assumir(22L, 11L));
        assertThrows(IllegalArgumentException.class,
                () -> assumirTicket.assumir(22L, 11L));

        entityManager.clear();
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE,
                conversas.findByIdAndEmpresaId(22L, 22L).orElseThrow().getStatus());
        assertEquals(Ticket.StatusTicket.PENDENTE,
                tickets.findByIdAndEmpresaId(22L, 22L).orElseThrow().getStatus());
    }
}
