package com.wxn.chatbot.telegram;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.wxn.chatbot.llm.RespostaAutomaticaService;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.repository.ConversaRepository;
class RespostaTelegramServiceTests {
 @Test void geraSomenteNoAtendimentoAutomatico() {
  var repo=mock(ConversaRepository.class); var llm=mock(RespostaAutomaticaService.class);
  var manager=mock(PlatformTransactionManager.class); when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
  var empresa=new Empresa(1L,"Empresa"); var cliente=new Cliente(2L,"81999990000"); cliente.vincularEmpresa(empresa);
  var conversa=new Conversa(3L,cliente,empresa,new Chatbot(4L,"Bot",empresa,"Olá"));
  conversa.adicionarMensagem(1L,"Oi",Mensagem.TipoRemetente.CLIENTE);
  when(repo.findByIdAndEmpresaId(3L,1L)).thenReturn(Optional.of(conversa)); when(llm.responder(3L,1L,2L)).thenReturn("Olá");
  var service=new RespostaTelegramService(repo,llm,manager); assertEquals("Olá",service.gerar(1L,3L));
  conversa.solicitarAtendimentoHumano(); assertThrows(IllegalStateException.class,()->service.gerar(1L,3L)); verify(llm,times(1)).responder(anyLong(),anyLong(),anyLong());
 }
}
