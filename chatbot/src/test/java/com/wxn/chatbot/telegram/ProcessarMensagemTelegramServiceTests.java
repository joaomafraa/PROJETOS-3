package com.wxn.chatbot.telegram;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
class ProcessarMensagemTelegramServiceTests {
 @Test void enviaRespostaESinalizaFalha() {
  var i=mock(IdentificacaoTelegramService.class); var r=mock(RegistrarMensagemTelegramService.class); var ia=mock(RespostaTelegramService.class); var f=mock(FilaEnvioTelegramService.class); var t=mock(TelegramCliente.class);
  var m=new MensagemTelegram(10,20,20,"Ana","Oi"); when(i.obterConversa(1L,2L,20,"Ana")).thenReturn(3L); when(r.registrar(1L,2L,m)).thenReturn(true); when(ia.respostaExistenteOuPendente(1L,3L)).thenReturn(""); when(ia.gerar(1L,3L)).thenReturn("Olá"); when(f.marcarIncerteza(1,10)).thenReturn(true); when(f.marcarEnviado(1,10)).thenReturn(true);
  var service=new ProcessarMensagemTelegramService(i,r,ia,f,t,mock(TransferenciaTelegramService.class)); assertTrue(service.processar(1,2,m)); verify(t).enviarMensagem(20,"Olá"); verify(f).marcarEnviado(1,10);
  doThrow(new IllegalStateException()).when(t).enviarMensagem(20,"Olá"); assertThrows(IllegalStateException.class,()->service.processar(1,2,m)); verify(f,times(2)).marcarIncerteza(1,10);
 }
 @Test void naoRepeteEnvioIncerto() {
  var i=mock(IdentificacaoTelegramService.class); var r=mock(RegistrarMensagemTelegramService.class); var ia=mock(RespostaTelegramService.class); var f=mock(FilaEnvioTelegramService.class); var t=mock(TelegramCliente.class);
  var m=new MensagemTelegram(10,20,20,"Ana","Oi"); when(i.obterConversa(1L,2L,20,"Ana")).thenReturn(3L); when(f.status(1,10)).thenReturn("INCERTO");
  assertFalse(new ProcessarMensagemTelegramService(i,r,ia,f,t,mock(TransferenciaTelegramService.class)).processar(1,2,m)); verifyNoInteractions(ia,t);
 }
}
