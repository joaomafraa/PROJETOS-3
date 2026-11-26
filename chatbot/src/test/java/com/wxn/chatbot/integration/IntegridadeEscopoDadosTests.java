package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.telegram.IdentificacaoTelegramService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:integridade-escopo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class IntegridadeEscopoDadosTests {

    @Autowired private EntityManager em;
    @Autowired private IdentificacaoTelegramService identificacaoTelegram;

    @BeforeEach
    void preparar() {
        Empresa empresaA = new Empresa(83001L, "Empresa A");
        Empresa empresaB = new Empresa(83002L, "Empresa B");
        em.persist(empresaA);
        em.persist(empresaB);

        Cliente clienteA = cliente(83001L, "+558199990001", empresaA);
        Cliente clienteA2 = cliente(83002L, "+558199990002", empresaA);
        Cliente clienteB = cliente(83003L, "+558199990003", empresaB);
        em.persist(clienteA);
        em.persist(clienteA2);
        em.persist(clienteB);

        Chatbot botA = new Chatbot(83001L, "Bot A", empresaA, "Ola");
        Chatbot botB = new Chatbot(83002L, "Bot B", empresaB, "Ola");
        em.persist(botA);
        em.persist(botB);
        em.persist(new Conversa(83001L, clienteA, empresaA, botA));
        em.persist(new Conversa(83002L, clienteA2, empresaA, botA));
        em.persist(new Conversa(83003L, clienteB, empresaB, botB));
        em.flush();
    }

    @Test
    void contatoTelegramNaoCombinaClienteEConversaDiferentes() {
        em.createNativeQuery("""
                insert into telegram_contatos (empresa_id, usuario_id, cliente_id, conversa_id)
                values (83001, 90001, 83001, 83002)
                """).executeUpdate();
        em.flush();
        em.clear();

        assertThrows(SecurityException.class,
                () -> identificacaoTelegram.obterConversa(83001L, 83001L, 90001L, "Teste"));
    }

    @Test
    void atualizacaoTelegramNaoApontaParaConversaDeOutraEmpresa() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery("""
                insert into telegram_atualizacoes (empresa_id, atualizacao_id, conversa_id)
                values (83001, 90002, 83003)
                """).executeUpdate());
    }

    @Test
    void contatoTelegramNaoApontaParaClienteDeOutraEmpresa() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery("""
                insert into telegram_contatos (empresa_id, usuario_id, cliente_id, conversa_id)
                values (83001, 90003, 83003, 83001)
                """).executeUpdate());
    }

    @Test
    void ticketNaoCombinaClienteEConversaDiferentes() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery("""
                insert into tickets
                    (id, protocolo, descricao, tipo, data_abertura, empresa_id,
                     cliente_id, conversa_origem_id, status)
                values
                    (83999, 'INDEVIDO-1', 'Nao deve persistir', 'SUPORTE', current_timestamp,
                     83001, 83001, 83002, 'PENDENTE')
                """).executeUpdate());
    }

    private static Cliente cliente(long id, String telefone, Empresa empresa) {
        Cliente cliente = new Cliente(id, telefone);
        cliente.vincularEmpresa(empresa);
        return cliente;
    }
}
