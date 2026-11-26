package com.wxn.chatbot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;
import com.wxn.chatbot.telegram.MensagemTelegram;
import com.wxn.chatbot.telegram.RegistrarMensagemTelegramService;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:repeticao-telegram;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class RepeticaoTelegramConcorrenteTests {
    private static final AtomicLong IDS = new AtomicLong(84000L);

    @Autowired private EntityManager em;
    @Autowired private RegistrarMensagemTelegramService registro;
    @Autowired private PlatformTransactionManager transacoes;

    @Test
    void mesmaAtualizacaoConcorrenteEhRegistradaUmaUnicaVez() throws Exception {
        long id = IDS.incrementAndGet();
        new TransactionTemplate(transacoes).executeWithoutResult(status -> preparar(id));
        MensagemTelegram mensagem = new MensagemTelegram(id, id, id, "Cliente", "Mensagem unica");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<Boolean> primeira = pool.submit(() -> registrarAposLargada(largada, id, mensagem));
            Future<Boolean> segunda = pool.submit(() -> registrarAposLargada(largada, id, mensagem));
            largada.countDown();

            List<Boolean> resultados = List.of(primeira.get(15, TimeUnit.SECONDS),
                    segunda.get(15, TimeUnit.SECONDS));
            assertEquals(1, resultados.stream().filter(Boolean::booleanValue).count(), resultados.toString());
        } finally {
            pool.shutdownNow();
        }

        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            assertEquals(1L, numero("select count(*) from telegram_atualizacoes where empresa_id = :id", id));
            assertEquals(1L, numero("select count(*) from mensagens where conversa_id = :id", id));
        });
    }

    private boolean registrarAposLargada(CountDownLatch largada, long id, MensagemTelegram mensagem)
            throws InterruptedException {
        largada.await();
        return registro.registrar(id, id, mensagem);
    }

    private void preparar(long id) {
        Empresa empresa = new Empresa(id, "Empresa concorrencia");
        em.persist(empresa);
        Cliente cliente = new Cliente(id, "+55819" + id);
        cliente.vincularEmpresa(empresa);
        em.persist(cliente);
        Chatbot bot = new Chatbot(id, "Bot", empresa, "Ola");
        em.persist(bot);
        em.persist(new Conversa(id, cliente, empresa, bot));
        em.flush();
        em.createNativeQuery("""
                insert into telegram_contatos (empresa_id, usuario_id, cliente_id, conversa_id)
                values (:id, :id, :id, :id)
                """).setParameter("id", id).executeUpdate();
    }

    private long numero(String sql, long id) {
        return ((Number) em.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
    }
}
