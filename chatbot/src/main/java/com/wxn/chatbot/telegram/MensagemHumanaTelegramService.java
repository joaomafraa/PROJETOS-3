package com.wxn.chatbot.telegram;
import jakarta.persistence.EntityManager;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

/** Prepara a resposta de atendente responsável, com entrega explicitamente rastreada. */
@Service
public class MensagemHumanaTelegramService {
 private final EntityManager em;
 private final ConversaRepository conversas;
 public MensagemHumanaTelegramService(EntityManager em, ConversaRepository conversas) { this.em=em; this.conversas=conversas; }
 @Transactional
 public long preparar(long conversaId, Atendente atendente, String texto) {
  if (atendente == null || texto == null || texto.isBlank() || texto.length()>4096) throw new IllegalArgumentException("Mensagem inválida.");
  long empresaId=atendente.getEmpresa().getId();
  Conversa conversa=conversas.buscarParaAtualizar(conversaId,empresaId).orElseThrow(()->new NoSuchElementException("Conversa não encontrada."));
  var contatos=em.createNativeQuery("select usuario_id from telegram_contatos where empresa_id=:empresa and conversa_id=:conversa")
   .setParameter("empresa",empresaId).setParameter("conversa",conversaId).getResultList();
  if(contatos.size()!=1) throw new NoSuchElementException("Canal Telegram não encontrado.");
  long chatId=((Number)contatos.get(0)).longValue();
  long id=Math.addExact(conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0),1);
  conversa.adicionarMensagem(id,texto.strip(),Mensagem.TipoRemetente.ATENDENTE,atendente);
  em.flush();
  em.createNativeQuery("insert into telegram_envios_atendente (empresa_id, conversa_id, mensagem_id, chat_id, conteudo, status) values (:empresa,:conversa,:mensagem,:chat,:conteudo,'PENDENTE')")
   .setParameter("empresa",empresaId).setParameter("conversa",conversaId).setParameter("mensagem",id).setParameter("chat",chatId).setParameter("conteudo",texto.strip()).executeUpdate();
  return ((Number) em.createNativeQuery("select id from telegram_envios_atendente where empresa_id=:empresa and conversa_id=:conversa and mensagem_id=:mensagem")
   .setParameter("empresa",empresaId).setParameter("conversa",conversaId).setParameter("mensagem",id).getSingleResult()).longValue();
 }
}
