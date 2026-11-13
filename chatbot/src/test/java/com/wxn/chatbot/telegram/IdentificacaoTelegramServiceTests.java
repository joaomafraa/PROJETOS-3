package com.wxn.chatbot.telegram;
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
class IdentificacaoTelegramServiceTests {
 @Autowired EntityManager em;
 @Autowired IdentificacaoTelegramService service;
 @Autowired RegistrarMensagemTelegramService registrar;
 @Autowired FilaEnvioTelegramService fila;
 @Autowired TransferenciaTelegramService transferencia;
 @Autowired MensagemHumanaTelegramService humana;
 @Autowired RevisaoEnvioTelegramService revisao;
 @Test void criaEVinculaSemTelefoneEIsolaEmpresas() {
  Empresa a=new Empresa(80001L,"A"), b=new Empresa(80002L,"B"); em.persist(a); em.persist(b);
  em.persist(new Chatbot(80001L,"Bot A",a,"Olá")); em.persist(new Chatbot(80002L,"Bot B",b,"Olá")); em.flush();
  Long primeira=service.obterConversa(80001L,80001L,777L,"Ana");
  assertEquals(primeira,service.obterConversa(80001L,80001L,777L,"Ana"));
  Long outra=service.obterConversa(80002L,80002L,777L,"Ana"); assertNotEquals(primeira,outra);
  assertNull(em.find(Conversa.class,primeira).getCliente().getNumeroTelefone());
  var msg = new MensagemTelegram(555L, 777L, 777L, "Ana", "Olá");
  assertTrue(registrar.registrar(80001L, 80001L, msg));
  assertFalse(registrar.registrar(80001L, 80001L, msg));
  assertEquals(1, em.find(Conversa.class, primeira).getMensagens().size());
  fila.preparar(80001L, 555L, 777L, "Olá, Ana");
  assertFalse(fila.marcarEnviado(80002L, 555L));
  assertTrue(fila.marcarIncerteza(80001L, 555L));
  assertTrue(fila.marcarEnviado(80001L, 555L));
  assertEquals(TransferenciaTelegramService.CONFIRMACAO, transferencia.solicitar(80001L, primeira));
  assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, em.find(Conversa.class, primeira).getStatus());
  assertEquals(TransferenciaTelegramService.CONFIRMACAO, transferencia.solicitar(80001L, primeira));
  assertEquals(2, em.find(Conversa.class, primeira).getMensagens().size());
  Atendente atendente = new Atendente(80001L, "Ana", "ana.teste@example.com", a, Atendente.PerfilAcesso.ATENDENTE);
  em.persist(atendente); em.find(Conversa.class, primeira).iniciarAtendimentoHumano(atendente); em.flush();
  assertTrue(humana.preparar(primeira, atendente, "Olá, posso ajudar?") > 0);
  assertEquals(3, em.find(Conversa.class, primeira).getMensagens().size());
  em.createNativeQuery("update telegram_envios_atendente set status='INCERTO' where conversa_id=:id").setParameter("id", primeira).executeUpdate();
  revisao.confirmar(primeira, 3L, atendente);
  assertEquals(1L, ((Number)em.createNativeQuery("select count(*) from telegram_revisoes_envio where atendente_id=80001").getSingleResult()).longValue());
  Atendente estranho = new Atendente(80002L, "Beto", "beto.teste@example.com", b, Atendente.PerfilAcesso.ATENDENTE);
  em.persist(estranho); em.flush();
  assertThrows(java.util.NoSuchElementException.class, () -> humana.preparar(primeira, estranho, "Oi"));
  assertThrows(IllegalArgumentException.class, () -> revisao.confirmar(primeira, 3L, estranho));
  assertThrows(IllegalArgumentException.class,()->service.obterConversa(80001L,80002L,778L,"Ana"));
 }
}
