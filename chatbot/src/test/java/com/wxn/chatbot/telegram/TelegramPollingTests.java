package com.wxn.chatbot.telegram;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
class TelegramPollingTests {
 @Test void confirmaOffsetApenasDepoisDoProcessamento() {
  var api=mock(TelegramCliente.class); var processar=mock(ProcessarMensagemTelegramService.class);
  var update=Map.of("update_id",100L,"message",Map.of("chat",Map.of("id",123L,"type","private"),"from",Map.of("id",123L,"first_name","Ana"),"text","Oi"));
  doReturn(List.of(update)).when(api).buscarAtualizacoes(0);
  when(processar.processar(eq(1L),eq(2L),any())).thenThrow(new IllegalStateException()).thenReturn(true);
  var polling=new TelegramPolling(api,processar,1,2); polling.consultar(); polling.consultar(); polling.consultar();
  verify(api,times(2)).buscarAtualizacoes(0); verify(api).buscarAtualizacoes(101);
 }
}


