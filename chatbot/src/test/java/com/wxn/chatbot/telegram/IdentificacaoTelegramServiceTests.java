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
  assertThrows(IllegalArgumentException.class,()->service.obterConversa(80001L,80002L,778L,"Ana"));
 }
}
