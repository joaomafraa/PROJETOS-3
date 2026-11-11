package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.dto.FiltroTickets;
import com.wxn.chatbot.dto.MensagemResumo;
import com.wxn.chatbot.dto.PaginaResumo;
import com.wxn.chatbot.dto.TicketDetalhe;
import com.wxn.chatbot.dto.TicketResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Ticket;
import com.wxn.chatbot.model.Ticket.StatusTicket;
import com.wxn.chatbot.model.Ticket.TipoSolicitacao;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:consulta-tickets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class ConsultaTicketsServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private ConsultaTicketsService consulta;

    // Empresa 1: tickets 1 a 5. Empresa 2: ticket 6.
    @BeforeEach
    void preparar() {
        Empresa empresa = new Empresa(1L, "WXN");
        Empresa outra = new Empresa(2L, "Outra");
        entityManager.persist(empresa);
        entityManager.persist(outra);

        Atendente ana = new Atendente(1L, "Ana", "ana@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE);
        Atendente bruno = new Atendente(2L, "Bruno", "bruno@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE);
        Atendente admin = new Atendente(3L, "Admin", "admin@example.com", empresa,
                Atendente.PerfilAcesso.ADMINISTRADOR);
        Atendente externo = new Atendente(4L, "Externo", "externo@example.com", outra,
                Atendente.PerfilAcesso.ATENDENTE);
        Atendente carla = new Atendente(5L, "Carla", "carla@example.com", empresa, Atendente.PerfilAcesso.ATENDENTE);
        for (Atendente atendente : List.of(ana, bruno, admin, externo, carla)) {
            entityManager.persist(atendente);
        }

        Chatbot bot = new Chatbot(1L, "Assistente", empresa, "Ola");
        Chatbot botExterno = new Chatbot(2L, "Bot externo", outra, "Ola");
        entityManager.persist(bot);
        entityManager.persist(botExterno);

        Cliente marina = new Cliente(1L, "+5581999999999");
        marina.alterarNome("Marina");
        marina.atualizarEmail("marina@example.com");
        marina.confirmarIdentidade();
        marina.vincularEmpresa(empresa);
        Cliente joao = new Cliente(2L, "+5581988887777");
        joao.vincularEmpresa(empresa);
        Cliente clienteExterno = new Cliente(3L, "+5511977776666");
        clienteExterno.alterarNome("Cliente externo");
        clienteExterno.vincularEmpresa(outra);
        entityManager.persist(marina);
        entityManager.persist(joao);
        entityManager.persist(clienteExterno);

        Conversa conversaMarina = new Conversa(1L, marina, empresa, bot);
        conversaMarina.adicionarMensagem(1L, "Meu sistema parou", Mensagem.TipoRemetente.CLIENTE);
        conversaMarina.adicionarMensagem(2L, "Vou abrir um chamado", Mensagem.TipoRemetente.CHATBOT);
        entityManager.persist(conversaMarina);

        Conversa conversaJoao = new Conversa(2L, joao, empresa, bot);
        conversaJoao.adicionarMensagem(1L, "Quero um orcamento", Mensagem.TipoRemetente.CLIENTE);
        conversaJoao.solicitarAtendimentoHumano();
        conversaJoao.iniciarAtendimentoHumano(ana);
        conversaJoao.adicionarMensagem(2L, "Claro, vou ajudar", Mensagem.TipoRemetente.ATENDENTE, ana);
        entityManager.persist(conversaJoao);

        Conversa conversaExterna = new Conversa(3L, clienteExterno, outra, botExterno);
        conversaExterna.adicionarMensagem(1L, "Mensagem de outra empresa", Mensagem.TipoRemetente.CLIENTE);
        entityManager.persist(conversaExterna);

        // 1: SUPORTE, pendente, sem responsavel
        entityManager.persist(new Ticket(1L, "WXN-1", "Sistema parou", TipoSolicitacao.SUPORTE, conversaMarina));
        // 2: SUPORTE, em atendimento com Ana
        Ticket t2 = new Ticket(2L, "WXN-2", "Erro no login", TipoSolicitacao.SUPORTE, conversaJoao);
        t2.atribuirResponsavel(ana);
        t2.iniciarAtendimento();
        entityManager.persist(t2);
        // 3: ORCAMENTO, pendente, ja atribuido ao Bruno
        Ticket t3 = new Ticket(3L, "WXN-3", "Orcamento anual", TipoSolicitacao.ORCAMENTO, conversaJoao);
        t3.atribuirResponsavel(bruno);
        entityManager.persist(t3);
        // 4: DEMONSTRACAO, concluido pela Ana
        Ticket t4 = new Ticket(4L, "WXN-4", "Apresentar sistema", TipoSolicitacao.DEMONSTRACAO, conversaMarina);
        t4.atribuirResponsavel(ana);
        t4.iniciarAtendimento();
        t4.concluir();
        entityManager.persist(t4);
        // 5: ORCAMENTO, pendente, sem responsavel
        entityManager.persist(new Ticket(5L, "WXN-5", "Orcamento extra", TipoSolicitacao.ORCAMENTO, conversaMarina));
        // 6: outra empresa
        entityManager.persist(new Ticket(6L, "OUT-6", "Suporte externo", TipoSolicitacao.SUPORTE, conversaExterna));

        entityManager.flush();
        entityManager.clear();
    }

    private Atendente atendente(long id) {
        return entityManager.find(Atendente.class, id);
    }

    private List<Long> ids(FiltroTickets filtro) {
        return consulta.listar(atendente(1L), filtro, 0, 100).conteudo().stream()
                .map(TicketResumo::id).sorted().toList();
    }

    @Test
    void semFiltroListaApenasTicketsDaEmpresaDoAtendente() {
        PaginaResumo<TicketResumo> pagina = consulta.listar(atendente(1L), null, 0, 20);

        assertEquals(5, pagina.totalElementos());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), pagina.conteudo().stream().map(TicketResumo::id).sorted().toList());

        PaginaResumo<TicketResumo> daOutra = consulta.listar(atendente(4L), FiltroTickets.nenhum(), 0, 20);
        assertEquals(List.of(6L), daOutra.conteudo().stream().map(TicketResumo::id).toList());
    }

    @Test
    void filtrosFuncionamIsoladamente() {
        assertEquals(List.of(1L, 2L), ids(new FiltroTickets(TipoSolicitacao.SUPORTE, null, null, false)));
        assertEquals(List.of(1L, 3L, 5L), ids(new FiltroTickets(null, StatusTicket.PENDENTE, null, false)));
        assertEquals(List.of(4L), ids(new FiltroTickets(null, StatusTicket.CONCLUIDO, null, false)));
        assertEquals(List.of(2L, 4L), ids(new FiltroTickets(null, null, 1L, false)));
        assertEquals(List.of(1L, 5L), ids(new FiltroTickets(null, null, null, true)));
        assertEquals(List.of(), ids(new FiltroTickets(TipoSolicitacao.VISITA_TECNICA, null, null, false)));
    }

    @Test
    void filtrosFuncionamCombinados() {
        assertEquals(List.of(5L), ids(new FiltroTickets(TipoSolicitacao.ORCAMENTO, StatusTicket.PENDENTE, null, true)));
        assertEquals(List.of(3L), ids(new FiltroTickets(TipoSolicitacao.ORCAMENTO, null, 2L, false)));
        assertEquals(List.of(2L), ids(new FiltroTickets(TipoSolicitacao.SUPORTE, StatusTicket.EM_ATENDIMENTO, 1L, false)));
        assertEquals(List.of(), ids(new FiltroTickets(TipoSolicitacao.SUPORTE, StatusTicket.CONCLUIDO, null, false)));
    }

    @Test
    void filtroPorResponsavelDeOutraEmpresaNaoExpoeNada() {
        assertEquals(List.of(), ids(new FiltroTickets(null, null, 4L, false)));
    }

    @Test
    void resumoTrazClienteEResponsavel() {
        TicketResumo t2 = consulta.listar(atendente(1L), new FiltroTickets(null, null, 1L, false), 0, 20)
                .conteudo().stream().filter(t -> t.id() == 2L).findFirst().orElseThrow();

        assertEquals("WXN-2", t2.protocolo());
        assertEquals(TipoSolicitacao.SUPORTE, t2.tipo());
        assertEquals(StatusTicket.EM_ATENDIMENTO, t2.status());
        assertEquals(2L, t2.clienteId());
        assertNull(t2.clienteNome());
        assertEquals(1L, t2.responsavelId());
        assertEquals("Ana", t2.responsavelNome());
        assertNotNull(t2.dataAbertura());
    }

    @Test
    void paginacaoEOrdemSaoPrevisiveis() {
        List<TicketResumo> todos = new ArrayList<>();
        List<Integer> tamanhos = new ArrayList<>();
        for (int numero = 0; numero < 3; numero++) {
            PaginaResumo<TicketResumo> pagina = consulta.listar(atendente(1L), null, numero, 2);
            assertEquals(numero, pagina.pagina());
            assertEquals(2, pagina.tamanho());
            assertEquals(5, pagina.totalElementos());
            assertEquals(3, pagina.totalPaginas());
            tamanhos.add(pagina.conteudo().size());
            todos.addAll(pagina.conteudo());
        }

        assertEquals(List.of(2, 2, 1), tamanhos);
        Set<Long> unicos = new HashSet<>(todos.stream().map(TicketResumo::id).toList());
        assertEquals(5, unicos.size(), "Nenhum ticket pode repetir ou sumir entre paginas.");

        // Mais recentes primeiro; empate na data e decidido pelo maior id.
        Comparator<TicketResumo> ordem = Comparator.comparing(TicketResumo::dataAbertura).reversed()
                .thenComparing(Comparator.comparing(TicketResumo::id).reversed());
        assertEquals(todos.stream().sorted(ordem).toList(), todos);

        // Repetir a consulta devolve a mesma ordem.
        assertEquals(todos.stream().map(TicketResumo::id).toList(),
                consulta.listar(atendente(1L), null, 0, 5).conteudo().stream().map(TicketResumo::id).toList());

        assertTrue(consulta.listar(atendente(1L), null, 9, 2).conteudo().isEmpty());
    }

    @Test
    void rejeitaPaginacaoEFiltrosInvalidos() {
        Atendente ana = atendente(1L);
        assertThrows(IllegalArgumentException.class, () -> consulta.listar(ana, null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> consulta.listar(ana, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> consulta.listar(ana, null, 0, ConsultaTicketsService.TAMANHO_MAXIMO_PAGINA + 1));
        assertThrows(IllegalArgumentException.class, () -> new FiltroTickets(null, null, 1L, true));
        assertThrows(IllegalArgumentException.class, () -> new FiltroTickets(null, null, 0L, false));
    }

    @Test
    void recusaAtendenteNuloOuInativo() {
        Atendente carla = atendente(5L);
        carla.desativar(atendente(3L));

        assertThrows(NullPointerException.class, () -> consulta.listar(null, null, 0, 10));
        assertThrows(SecurityException.class, () -> consulta.listar(carla, null, 0, 10));
        assertThrows(SecurityException.class, () -> consulta.detalhar(1L, carla));
    }

    @Test
    void detalhesTrazemSolicitanteConversaEHistoricoCorretos() {
        TicketDetalhe detalhe = consulta.detalhar(1L, atendente(1L));

        assertEquals("WXN-1", detalhe.protocolo());
        assertEquals("Sistema parou", detalhe.descricao());
        assertEquals(StatusTicket.PENDENTE, detalhe.status());
        assertNull(detalhe.responsavelId());
        assertNull(detalhe.responsavelNome());
        assertNull(detalhe.dataConclusao());

        assertEquals(1L, detalhe.solicitante().id());
        assertEquals("Marina", detalhe.solicitante().nome());
        assertEquals("marina@example.com", detalhe.solicitante().email());
        assertEquals("+5581999999999", detalhe.solicitante().numeroTelefone());
        assertTrue(detalhe.solicitante().identidadeConfirmada());

        assertEquals(1L, detalhe.conversaOrigem().id());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, detalhe.conversaOrigem().status());
        assertEquals("Assistente", detalhe.conversaOrigem().chatbotNome());

        assertEquals(List.of("Meu sistema parou", "Vou abrir um chamado"),
                detalhe.historico().stream().map(MensagemResumo::conteudo).toList());
        assertEquals(List.of("Marina", "Assistente"),
                detalhe.historico().stream().map(MensagemResumo::autor).toList());
    }

    @Test
    void detalhesDeOutroTicketMostramOutraConversa() {
        TicketDetalhe detalhe = consulta.detalhar(2L, atendente(2L));

        assertEquals(1L, detalhe.responsavelId());
        assertEquals("Ana", detalhe.responsavelNome());
        assertNull(detalhe.solicitante().nome());
        assertNull(detalhe.solicitante().email());
        assertFalse(detalhe.solicitante().identidadeConfirmada());
        assertEquals(2L, detalhe.conversaOrigem().id());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, detalhe.conversaOrigem().status());
        assertEquals(List.of("Quero um orcamento", "Claro, vou ajudar"),
                detalhe.historico().stream().map(MensagemResumo::conteudo).toList());
        assertEquals(Mensagem.TipoRemetente.ATENDENTE, detalhe.historico().getLast().remetente());
        assertEquals("Ana", detalhe.historico().getLast().autor());

        TicketDetalhe concluido = consulta.detalhar(4L, atendente(1L));
        assertEquals(StatusTicket.CONCLUIDO, concluido.status());
        assertNotNull(concluido.dataConclusao());
    }

    @Test
    void naoExpoeTicketDeOutraEmpresaNemInexistente() {
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(6L, atendente(1L)));
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(1L, atendente(4L)));
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(999L, atendente(1L)));
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(0L, atendente(1L)));
        assertThrows(IllegalArgumentException.class, () -> consulta.detalhar(null, atendente(1L)));
    }

    @Test
    void responsaveisSaoDaMesmaEmpresaEMarcamOAtendenteAtual() {
        atendente(5L).desativar(atendente(3L));

        var opcoes = consulta.listarResponsaveis(atendente(2L));

        // Ordem por nome; Carla aparece mesmo inativa, porque pode ter tickets antigos.
        assertEquals(List.of("Admin", "Ana", "Bruno", "Carla"),
                opcoes.stream().map(com.wxn.chatbot.dto.ResponsavelOpcao::nome).toList());
        assertTrue(opcoes.stream().noneMatch(opcao -> opcao.id() == 4L), "Atendente de outra empresa nao aparece.");
        assertEquals(List.of(2L), opcoes.stream().filter(com.wxn.chatbot.dto.ResponsavelOpcao::voce)
                .map(com.wxn.chatbot.dto.ResponsavelOpcao::id).toList());
        assertFalse(opcoes.stream().filter(opcao -> opcao.id() == 5L).findFirst().orElseThrow().ativo());

        assertEquals(List.of("Externo"), consulta.listarResponsaveis(atendente(4L)).stream()
                .map(com.wxn.chatbot.dto.ResponsavelOpcao::nome).toList());
    }

    @Test
    void responsaveisRecusaAtendenteNuloOuInativo() {
        Atendente carla = atendente(5L);
        carla.desativar(atendente(3L));

        assertThrows(NullPointerException.class, () -> consulta.listarResponsaveis(null));
        assertThrows(SecurityException.class, () -> consulta.listarResponsaveis(carla));
    }
}
