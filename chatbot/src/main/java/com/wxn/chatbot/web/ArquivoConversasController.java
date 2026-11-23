package com.wxn.chatbot.web;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.security.ContextoAcesso;
@RestController
@RequestMapping("/api/arquivo-conversas")
public class ArquivoConversasController {
 private final EntityManager em; private final ConversaRepository conversas; private final ContextoAcesso contexto;
 public ArquivoConversasController(EntityManager em,ConversaRepository conversas,ContextoAcesso contexto){this.em=em;this.conversas=conversas;this.contexto=contexto;}
 @GetMapping @Transactional(readOnly=true)
 public List<Long> listar(){Atendente a=contexto.atendenteAtual();return em.createNativeQuery("select ar.conversa_id from conversas_arquivadas ar join conversas c on c.id=ar.conversa_id where ar.atendente_id=:atendente and c.empresa_id=:empresa").setParameter("atendente",a.getId()).setParameter("empresa",a.getEmpresa().getId()).getResultList().stream().map(x->((Number)x).longValue()).toList();}
 @GetMapping("/itens") @Transactional(readOnly=true)
 public List<com.wxn.chatbot.dto.ConversaResumo> itens() {
  Atendente a=contexto.atendenteAtual();
  return listar().stream().map(id -> conversas.findByIdAndEmpresaId(id,a.getEmpresa().getId()))
   .flatMap(java.util.Optional::stream)
   .map(c -> new com.wxn.chatbot.dto.ConversaResumo(c.getId(),c.getCliente().getId(),c.getCliente().getNome(),c.getCliente().getNumeroTelefone(),c.getChatbot().getNome(),c.getDataInicio(),c.getStatus())).toList();
 }
 @PostMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
 public void arquivar(@PathVariable long id){Atendente a=contexto.atendenteAtual();conversas.buscarParaAtualizar(id,a.getEmpresa().getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Conversa não encontrada."));
  var existentes=em.createNativeQuery("select conversa_id from conversas_arquivadas where atendente_id=:atendente and conversa_id=:conversa").setParameter("atendente",a.getId()).setParameter("conversa",id).getResultList();
  if(existentes.isEmpty())em.createNativeQuery("insert into conversas_arquivadas (atendente_id,conversa_id) values (:atendente,:conversa)").setParameter("atendente",a.getId()).setParameter("conversa",id).executeUpdate();}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
 public void restaurar(@PathVariable long id){Atendente a=contexto.atendenteAtual();em.createNativeQuery("delete from conversas_arquivadas where atendente_id=:atendente and conversa_id=:conversa").setParameter("atendente",a.getId()).setParameter("conversa",id).executeUpdate();}
}
