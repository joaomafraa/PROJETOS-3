package com.wxn.chatbot.telegram;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.wxn.chatbot.model.Atendente;
@Service
public class RevisaoEnvioTelegramService {
 private final EntityManager em; private final TelegramCliente telegram; private final TransactionTemplate tx;
 public RevisaoEnvioTelegramService(EntityManager em,TelegramCliente telegram,PlatformTransactionManager manager){this.em=em;this.telegram=telegram;this.tx=new TransactionTemplate(manager);}
 private void validar(long conversa,Atendente a){
  if(a==null || !a.isAtivo()) throw new SecurityException("Atendente inativo.");
  var linhas=em.createNativeQuery("select responsavel_id, status from conversas where id=:c and empresa_id=:e")
   .setParameter("c",conversa).setParameter("e",a.getEmpresa().getId()).getResultList();
  if(linhas.size()!=1) throw new IllegalArgumentException("Conversa não encontrada.");
  Object[] dados=(Object[])linhas.get(0);
  if(dados[0]==null || ((Number)dados[0]).longValue()!=a.getId()
    || !"EM_ATENDIMENTO_HUMANO".equals(dados[1]))
   throw new SecurityException("Somente o responsável pode revisar o envio.");
 }
 public void confirmar(long conversa,long mensagem,Atendente a){
  tx.executeWithoutResult(s->{validar(conversa,a); var linhas=em.createNativeQuery("select id from telegram_envios_atendente where conversa_id=:c and mensagem_id=:m and empresa_id=:e and status='INCERTO' for update")
   .setParameter("c",conversa).setParameter("m",mensagem).setParameter("e",a.getEmpresa().getId()).getResultList();
   if(linhas.size()!=1) throw new IllegalStateException("Envio não disponível para revisão.");
   em.createNativeQuery("insert into telegram_revisoes_envio (envio_id,atendente_id,data_confirmacao) values (:id,:a,CURRENT_TIMESTAMP)")
    .setParameter("id",((Number)linhas.get(0)).longValue()).setParameter("a",a.getId()).executeUpdate();
  });
 }
 public void reenviar(long conversa,long mensagem,Atendente a){
  Object[] dados=tx.execute(s->{validar(conversa,a);
   var linhas=em.createNativeQuery("select id,chat_id,conteudo from telegram_envios_atendente where conversa_id=:c and mensagem_id=:m and empresa_id=:e and status='INCERTO' and not exists (select 1 from telegram_revisoes_envio r where r.envio_id=telegram_envios_atendente.id) for update")
    .setParameter("c",conversa).setParameter("m",mensagem).setParameter("e",a.getEmpresa().getId()).getResultList();
   if(linhas.size()!=1) throw new IllegalStateException("Envio não disponível para reenvio."); Object[] v=(Object[])linhas.get(0);
   em.createNativeQuery("update telegram_envios_atendente set status='PENDENTE' where id=:id and empresa_id=:e")
    .setParameter("id",v[0]).setParameter("e",a.getEmpresa().getId()).executeUpdate(); return v;
  });
  try {telegram.enviarMensagem(((Number)dados[1]).longValue(),(String)dados[2]);}
  catch(RuntimeException erro){atualizar(((Number)dados[0]).longValue(),a.getEmpresa().getId(),"INCERTO");throw erro;}
  atualizar(((Number)dados[0]).longValue(),a.getEmpresa().getId(),"ENVIADO");
 }
 private void atualizar(long id,long empresaId,String status){tx.executeWithoutResult(s->em.createNativeQuery("update telegram_envios_atendente set status=:status where id=:id and empresa_id=:empresa and status='PENDENTE'").setParameter("status",status).setParameter("id",id).setParameter("empresa",empresaId).executeUpdate());}
}
