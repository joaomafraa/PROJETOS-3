package com.wxn.chatbot.web;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.wxn.chatbot.security.ContextoAcesso;
import com.wxn.chatbot.service.AtendimentoHumanoService;
import com.wxn.chatbot.telegram.EnvioHumanoTelegramService;
@RestController
@RequestMapping("/api/conversas")
public class AtendimentoTelegramController {
 private final jakarta.persistence.EntityManager em;
 private final com.wxn.chatbot.telegram.RevisaoEnvioTelegramService revisao;
 private final ContextoAcesso contexto; private final AtendimentoHumanoService atendimento; private final EnvioHumanoTelegramService envio;
 public AtendimentoTelegramController(ContextoAcesso contexto, AtendimentoHumanoService atendimento, EnvioHumanoTelegramService envio, jakarta.persistence.EntityManager em, com.wxn.chatbot.telegram.RevisaoEnvioTelegramService revisao) {this.revisao=revisao;this.em=em;this.contexto=contexto;this.atendimento=atendimento;this.envio=envio;}
 @PostMapping("/{id}/assumir") public void assumir(@PathVariable Long id) {try { atendimento.assumir(id,contexto.atendenteIdAtual()); } catch(IllegalArgumentException | IllegalStateException e) {throw new ResponseStatusException(HttpStatus.CONFLICT,"Não foi possível assumir a conversa.");} }
 public record StatusEnvio(Long mensagemId, String status) {}
 @GetMapping("/{id}/envios")
 @org.springframework.transaction.annotation.Transactional(readOnly=true)
 public java.util.List<StatusEnvio> envios(@PathVariable Long id) {
  Long empresaId=contexto.empresaIdAtual();
  java.util.List<?> linhas=em.createNativeQuery("select e.mensagem_id, case when r.envio_id is not null then 'CONFIRMADO' else e.status end from telegram_envios_atendente e left join telegram_revisoes_envio r on r.envio_id=e.id where e.empresa_id=:empresa and e.conversa_id=:conversa")
    .setParameter("empresa",empresaId).setParameter("conversa",id).getResultList();
  return linhas.stream().map(linha -> { Object[] valores=(Object[])linha; return new StatusEnvio(((Number)valores[0]).longValue(),(String)valores[1]); }).toList();
 }
 @PostMapping("/{id}/envios/{mensagem}/confirmar") public void confirmar(@PathVariable Long id,@PathVariable Long mensagem) {try {revisao.confirmar(id,mensagem,contexto.atendenteAtual());} catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Envio já revisado ou indisponível.");}}
 @PostMapping("/{id}/envios/{mensagem}/reenviar") public void reenviar(@PathVariable Long id,@PathVariable Long mensagem) {try {revisao.reenviar(id,mensagem,contexto.atendenteAtual());} catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Não foi possível confirmar o reenvio.");}}
 public record Texto(String conteudo) {}
 @PostMapping("/{id}/mensagens") public void enviar(@PathVariable Long id,@RequestBody Texto texto) {try {envio.enviar(id,contexto.atendenteAtual(),texto.conteudo());} catch(IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Não foi possível preparar a mensagem.");} catch(IllegalStateException e) {throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Envio sem confirmação; confira antes de tentar novamente.");} }
}
