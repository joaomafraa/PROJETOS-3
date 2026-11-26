package com.wxn.chatbot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wxn.chatbot.model.Atendente.PerfilAcesso;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;

// Regras do aviso de novo chamado (HU05.3), sem banco.
class NotificacaoChamadoTests {

    private Empresa empresaA;
    private Atendente admin;
    private Atendente bruno;
    private Ticket chamado;

    @BeforeEach
    void preparar() {
        empresaA = new Empresa(1L, "Empresa A");
        admin = new Atendente(10L, "Ana", "ana@a.example", empresaA, PerfilAcesso.ADMINISTRADOR);
        bruno = new Atendente(11L, "Bruno", "bruno@a.example", empresaA, PerfilAcesso.ATENDENTE);

        Cliente cliente = new Cliente(1L, "+5581999990001");
        cliente.vincularEmpresa(empresaA);
        Chatbot bot = new Chatbot(1L, "Bot A", empresaA, "Olá");
        Conversa conversa = new Conversa(1L, cliente, empresaA, bot);
        chamado = new Ticket(1L, "CH-00000000000000000001", "Sem acesso ao sistema",
                Ticket.TipoSolicitacao.SUPORTE, conversa);
    }

    @Test
    void criaAvisoDeNovoChamadoParaAMesmaEmpresa() {
        NotificacaoChamado n = new NotificacaoChamado(chamado, bruno);

        assertEquals(empresaA, n.getEmpresa());
        assertEquals(TipoNotificacao.NOVO_TICKET, n.getTipo());
        assertFalse(n.isLida());
        assertTrue(!n.getCriadaEm().isAfter(Instant.now()));
    }

    @Test
    void destinatarioDeOutraEmpresaEhRecusado() {
        Empresa empresaB = new Empresa(2L, "Empresa B");
        Atendente elisa = new Atendente(20L, "Elisa", "elisa@b.example", empresaB, PerfilAcesso.ATENDENTE);
        assertThrows(IllegalArgumentException.class, () -> new NotificacaoChamado(chamado, elisa));
    }

    @Test
    void atendenteInativoNaoRecebe() {
        bruno.desativar(admin);
        assertThrows(IllegalStateException.class, () -> new NotificacaoChamado(chamado, bruno));
    }

    @Test
    void soODestinatarioMarcaComoLida() {
        NotificacaoChamado n = new NotificacaoChamado(chamado, bruno);

        assertThrows(IllegalArgumentException.class, () -> n.marcarComoLida(admin));
        assertFalse(n.isLida());

        n.marcarComoLida(bruno);
        assertTrue(n.isLida());
    }

    @Test
    void marcarDeNovoNaoMudaADataDeLeitura() throws InterruptedException {
        NotificacaoChamado n = new NotificacaoChamado(chamado, bruno);
        n.marcarComoLida(bruno);
        Instant primeira = n.getLidaEm();
        Thread.sleep(5);
        n.marcarComoLida(bruno);
        assertEquals(primeira, n.getLidaEm());
    }
}
