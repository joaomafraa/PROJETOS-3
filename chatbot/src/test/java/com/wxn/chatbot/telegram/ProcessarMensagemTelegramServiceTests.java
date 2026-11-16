package com.wxn.chatbot.telegram;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
class ProcessarMensagemTelegramServiceTests {
 @Test void enviaRespostaESinalizaFalha() {
  var i=mock(IdentificacaoTelegramService.class); var r=mock(RegistrarMensagemTelegramService.class); var ia=mock(RespostaTelegramService.class); var f=mock(FilaEnvioTelegramService.class); var t=mock(TelegramCliente.class);
  var m=new MensagemTelegram(10,20,20,"Ana","Oi"); when(i.obterConversa(1L,2L,20,"Ana")).thenReturn(3L); when(r.registrar(1L,2L,m)).thenReturn(true); when(ia.respostaExistenteOuPendente(1L,3L)).thenReturn(""); when(ia.gerar(1L,3L)).thenReturn("Olá"); when(f.marcarIncerteza(1,10)).thenReturn(true); when(f.marcarEnviado(1,10)).thenReturn(true);
  var service=new ProcessarMensagemTelegramService(i,r,ia,f,t,mock(TransferenciaTelegramService.class),mock(com.wxn.chatbot.security.ValidacaoClienteService.class)); assertTrue(service.processar(1,2,m)); verify(t).enviarMensagem(20,"Olá"); verify(f).marcarEnviado(1,10);
  doThrow(new IllegalStateException()).when(t).enviarMensagem(20,"Olá"); assertThrows(FalhaEtapaTelegramException.class,()->service.processar(1,2,m)); verify(f,times(2)).marcarIncerteza(1,10);
 }
 @Test void naoRepeteEnvioIncerto() {
  var i=mock(IdentificacaoTelegramService.class); var r=mock(RegistrarMensagemTelegramService.class); var ia=mock(RespostaTelegramService.class); var f=mock(FilaEnvioTelegramService.class); var t=mock(TelegramCliente.class);
  var m=new MensagemTelegram(10,20,20,"Ana","Oi"); when(i.obterConversa(1L,2L,20,"Ana")).thenReturn(3L); when(f.status(1,10)).thenReturn("INCERTO");
  assertFalse(new ProcessarMensagemTelegramService(i,r,ia,f,t,mock(TransferenciaTelegramService.class),mock(com.wxn.chatbot.security.ValidacaoClienteService.class)).processar(1,2,m)); verifyNoInteractions(ia,t);
 }
 @Test void codigoNaoEntraNoHistoricoNemNoLlm() {
  var i=mock(IdentificacaoTelegramService.class); var r=mock(RegistrarMensagemTelegramService.class); var ia=mock(RespostaTelegramService.class); var f=mock(FilaEnvioTelegramService.class); var t=mock(TelegramCliente.class);
  var validacao=mock(com.wxn.chatbot.security.ValidacaoClienteService.class);
  var mensagem=new MensagemTelegram(11,20,20,"Ana","/codigo 123456");
  when(i.obterConversa(1L,2L,20,"Ana")).thenReturn(3L);
  when(validacao.confirmar(1L,3L,"123456")).thenReturn(true);
  when(f.marcarIncerteza(1,11)).thenReturn(true); when(f.marcarEnviado(1,11)).thenReturn(true);
  assertTrue(new ProcessarMensagemTelegramService(i,r,ia,f,t,mock(TransferenciaTelegramService.class),validacao).processar(1,2,mensagem));
  var captor=org.mockito.ArgumentCaptor.forClass(MensagemTelegram.class);
  verify(r).registrar(eq(1L),eq(2L),captor.capture());
  assertEquals("[codigo de verificacao omitido]",captor.getValue().texto());
  verifyNoInteractions(ia);
 }
}

