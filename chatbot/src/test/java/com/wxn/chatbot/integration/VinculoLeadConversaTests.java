package com.wxn.chatbot.integration;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.service.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:vinculolead;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Transactional
class VinculoLeadConversaTests {
 @Autowired EntityManager em; @Autowired VinculoLeadConversaService servico;
 @Test void associaResumoSemPermitirOutraEmpresa() {
  Empresa a=new Empresa(1L,"A"),b=new Empresa(2L,"B");em.persist(a);em.persist(b);
  Atendente aa=new Atendente(1L,"Ana","ana@example.com",a,Atendente.PerfilAcesso.ATENDENTE);em.persist(aa);
  Cliente ca=new Cliente(1L,"81999990000");ca.vincularEmpresa(a);em.persist(ca);
  Cliente cb=new Cliente(2L,"81999990001");cb.vincularEmpresa(b);em.persist(cb);
  Chatbot ba=new Chatbot(1L,"A",a,"Olá"),bb=new Chatbot(2L,"B",b,"Olá");em.persist(ba);em.persist(bb);
  Conversa conversa=new Conversa(1L,ca,a,ba);conversa.adicionarMensagem(1L,"Quero orçamento",Mensagem.TipoRemetente.CLIENTE);em.persist(conversa);
  em.persist(new Conversa(2L,cb,b,bb));
  Lead lead=new Lead(a,"Ana","ana@example.com",Lead.Origem.SITE,"Orçamento");em.persist(lead);em.flush();
  assertNull(servico.consultar(lead.getId(),aa));
  assertThrows(IllegalArgumentException.class,()->servico.vincular(lead.getId(),2L,aa));
  servico.vincular(lead.getId(),1L,aa);
  assertEquals("Quero orçamento",servico.consultar(lead.getId(),aa).trechoInicialCliente().trecho());
 }
}
