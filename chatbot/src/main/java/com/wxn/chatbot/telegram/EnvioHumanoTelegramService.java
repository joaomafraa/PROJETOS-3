package com.wxn.chatbot.telegram;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.wxn.chatbot.model.Atendente;
@Service
public class EnvioHumanoTelegramService {
 private final MensagemHumanaTelegramService mensagens; private final TelegramCliente telegram; private final EntityManager em; private final TransactionTemplate tx;
 public EnvioHumanoTelegramService(MensagemHumanaTelegramService mensagens, TelegramCliente telegram, EntityManager em, PlatformTransactionManager manager) {this.mensagens=mensagens;this.telegram=telegram;this.em=em;this.tx=new TransactionTemplate(manager);}
 public void enviar(long conversaId, Atendente atendente, String texto) {
  long empresaId=atendente.getEmpresa().getId(); long id=mensagens.preparar(conversaId,atendente,texto);
  Object[] dados=tx.execute(s->{
   var registros=em.createNativeQuery("select chat_id, conteudo from telegram_envios_atendente where id=:id and empresa_id=:empresa and status='PENDENTE'").setParameter("id",id).setParameter("empresa",empresaId).getResultList();
   if(registros.size()!=1) throw new IllegalStateException("Envio indisponível.");
   int alterados=em.createNativeQuery("update telegram_envios_atendente set status='INCERTO' where id=:id and empresa_id=:empresa and status='PENDENTE'").setParameter("id",id).setParameter("empresa",empresaId).executeUpdate();
   if(alterados!=1) throw new IllegalStateException("Envio já processado."); return (Object[])registros.get(0);
  });
  telegram.enviarMensagem(((Number)dados[0]).longValue(),(String)dados[1]);
  tx.executeWithoutResult(s->em.createNativeQuery("update telegram_envios_atendente set status='ENVIADO' where id=:id and empresa_id=:empresa and status='INCERTO'").setParameter("id",id).setParameter("empresa",empresaId).executeUpdate());
 }
}
