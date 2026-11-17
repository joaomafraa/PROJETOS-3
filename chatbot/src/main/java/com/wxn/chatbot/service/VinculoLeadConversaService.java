package com.wxn.chatbot.service;

import com.wxn.chatbot.dto.ResumoEstruturadoConversa;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VinculoLeadConversaService {
 private final LeadRepository leads; private final ConversaRepository conversas;
 private final ResumoEstruturadoConversaService resumo; private final EntityManager em;
 public VinculoLeadConversaService(LeadRepository leads, ConversaRepository conversas, ResumoEstruturadoConversaService resumo, EntityManager em) {
  this.leads=leads;this.conversas=conversas;this.resumo=resumo;this.em=em;
 }
 @Transactional
 public void vincular(long leadId,long conversaId,Atendente atendente) {
  if(atendente==null || !atendente.isAtivo()) throw new SecurityException("Atendente inativo.");
  long empresa=atendente.getEmpresa().getId();
  var lead=leads.findByIdAndEmpresaId(leadId,empresa).orElseThrow(()->new IllegalArgumentException("Lead não encontrado."));
  conversas.buscarParaAtualizar(conversaId,empresa).orElseThrow(()->new IllegalArgumentException("Conversa não encontrada."));
  em.lock(lead, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  int atualizados=em.createNativeQuery("update lead_conversas set conversa_id=:conversa where lead_id=:lead and empresa_id=:empresa")
   .setParameter("conversa",conversaId).setParameter("lead",leadId).setParameter("empresa",empresa).executeUpdate();
  if(atualizados==0) em.createNativeQuery("insert into lead_conversas (lead_id,conversa_id,empresa_id) values (:lead,:conversa,:empresa)")
   .setParameter("lead",leadId).setParameter("conversa",conversaId).setParameter("empresa",empresa).executeUpdate();
 }
 @Transactional(readOnly=true)
 public ResumoEstruturadoConversa consultar(long leadId,Atendente atendente) {
  if(atendente==null || !atendente.isAtivo()) throw new SecurityException("Atendente inativo.");
  long empresa=atendente.getEmpresa().getId();
  leads.findByIdAndEmpresaId(leadId,empresa).orElseThrow(()->new IllegalArgumentException("Lead não encontrado."));
  var linhas=em.createNativeQuery("select conversa_id from lead_conversas where lead_id=:lead and empresa_id=:empresa")
   .setParameter("lead",leadId).setParameter("empresa",empresa).getResultList();
  if(linhas.isEmpty()) return null;
  return resumo.gerar(((Number)linhas.get(0)).longValue(),atendente);
 }
}
