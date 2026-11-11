package com.wxn.chatbot.telegram;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class MensagemTelegramTests {
 @Test void aceitaMensagemPrivadaSemTelefone() {
  var update=Map.of("update_id",123L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L,"first_name","Ana"),"text"," Olá "));
  var mensagem=MensagemTelegram.interpretar(update).orElseThrow(); assertEquals(55L,mensagem.usuarioId()); assertEquals("Olá",mensagem.texto());
 }
 @Test void ignoraGrupoEBot() {
  var grupo=Map.of("update_id",1,"message",Map.of("chat",Map.of("id",-1,"type","group"),"from",Map.of("id",55),"text","oi")); assertTrue(MensagemTelegram.interpretar(grupo).isEmpty());
  var bot=Map.of("update_id",2,"message",Map.of("chat",Map.of("id",55,"type","private"),"from",Map.of("id",55,"is_bot",true),"text","oi")); assertTrue(MensagemTelegram.interpretar(bot).isEmpty());
 }
}
