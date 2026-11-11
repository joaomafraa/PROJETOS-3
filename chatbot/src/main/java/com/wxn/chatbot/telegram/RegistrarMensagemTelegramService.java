package com.wxn.chatbot.telegram;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

@Service
public class RegistrarMensagemTelegramService {
 private final IdentificacaoTelegramService identificacao;
 private final ConversaRepository conversas;
 private final EntityManager em;
 public RegistrarMensagemTelegramService(IdentificacaoTelegramService identificacao, ConversaRepository conversas, EntityManager em) {
  this.identificacao=identificacao; this.conversas=conversas; this.em=em;
 }
 @Transactional
 public boolean registrar(Long empresaId, Long chatbotId, MensagemTelegram mensagem) {
  if (mensagem == null) throw new IllegalArgumentException("Mensagem obrigatória.");
  Long conversaId=identificacao.obterConversa(empresaId,chatbotId,mensagem.usuarioId(),mensagem.nome());
  Conversa conversa=conversas.buscarParaAtualizar(conversaId,empresaId).orElseThrow();
  var existentes=em.createNativeQuery("select atualizacao_id from telegram_atualizacoes where empresa_id = :empresa and atualizacao_id = :id")
    .setParameter("empresa",empresaId).setParameter("id",mensagem.atualizacaoId()).getResultList();
  if(!existentes.isEmpty()) return false;
  long id=conversa.getMensagens().stream().mapToLong(Mensagem::getId).max().orElse(0L)+1;
  conversa.adicionarMensagem(id,mensagem.texto(),Mensagem.TipoRemetente.CLIENTE);
  em.flush();
  em.createNativeQuery("insert into telegram_atualizacoes (empresa_id, atualizacao_id, conversa_id) values (:empresa,:id,:conversa)")
    .setParameter("empresa",empresaId).setParameter("id",mensagem.atualizacaoId()).setParameter("conversa",conversaId).executeUpdate();
  return true;
 }
}
