package com.wxn.chatbot.web;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.wxn.chatbot.dto.ResumoEstruturadoConversa;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.VinculoLeadConversaService;
@RestController
@RequestMapping("/api/leads/{leadId}/conversa")
public class LeadConversaController {
 private final VinculoLeadConversaService servico; private final ContextoAcesso contexto;
 public LeadConversaController(VinculoLeadConversaService servico,ContextoAcesso contexto){this.servico=servico;this.contexto=contexto;}
 public record Vinculo(Long conversaId) {}
 @PutMapping public void vincular(@PathVariable long leadId,@RequestBody Vinculo dados){
  if(dados.conversaId()==null || dados.conversaId()<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe a conversa.");
  try {servico.vincular(leadId,dados.conversaId(),contexto.atendenteAtual());} catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lead ou conversa não encontrados.");}
 }
 @GetMapping public Response resumo(@PathVariable long leadId){
  try{return new Response(servico.consultar(leadId,contexto.atendenteAtual()));} catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lead ou conversa não encontrados.");}
 }
 public record Response(ResumoEstruturadoConversa resumo) {}
}
