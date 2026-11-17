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
 @Test void contatoProprioPreencheTelefoneInternacionalSemIrParaOTexto() {
  var update=Map.of("update_id",7L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L,"first_name","Ana"),
    "contact",Map.of("phone_number","5581977770001","user_id",55L)));
  var mensagem=MensagemTelegram.interpretar(update).orElseThrow();
  assertEquals("+5581977770001",mensagem.telefone()); assertTrue(mensagem.compartilhouContato()); assertFalse(mensagem.texto().contains("5581"));
  var comMais=Map.of("update_id",8L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L),
    "contact",Map.of("phone_number","+5581977770001","user_id",55L)));
  assertEquals("+5581977770001",MensagemTelegram.interpretar(comMais).orElseThrow().telefone());
 }
 @Test void contatoDeOutraPessoaOuSemDonoNaoPreencheTelefone() {
  var outro=Map.of("update_id",9L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L),
    "contact",Map.of("phone_number","5581977770001","user_id",66L)));
  assertNull(MensagemTelegram.interpretar(outro).orElseThrow().telefone());
  var semDono=Map.of("update_id",10L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L),
    "contact",Map.of("phone_number","5581977770001")));
  assertNull(MensagemTelegram.interpretar(semDono).orElseThrow().telefone());
 }
 @Test void textoComumNaoTemTelefone() {
  var update=Map.of("update_id",11L,"message",Map.of("chat",Map.of("id",55L,"type","private"),"from",Map.of("id",55L),"text","81977770001"));
  var mensagem=MensagemTelegram.interpretar(update).orElseThrow(); assertNull(mensagem.telefone()); assertFalse(mensagem.compartilhouContato());
 }
}
