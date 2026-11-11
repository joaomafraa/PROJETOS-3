package com.wxn.chatbot.telegram;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import jakarta.persistence.EntityManager;
import com.wxn.chatbot.model.*;
class EnvioHumanoTelegramServiceTests {
 @Test void falhaAntesDoEnvioNaoChamaTelegram() {
  var mensagens=mock(MensagemHumanaTelegramService.class); var api=mock(TelegramCliente.class); var em=mock(EntityManager.class); var tm=mock(PlatformTransactionManager.class); when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
  var atendente=new Atendente(1L,"Ana","ana@example.com",new Empresa(1L,"Empresa"),Atendente.PerfilAcesso.ATENDENTE);
  when(mensagens.preparar(2,atendente,"Olá")).thenThrow(new IllegalArgumentException());
  assertThrows(IllegalArgumentException.class,()->new EnvioHumanoTelegramService(mensagens,api,em,tm).enviar(2,atendente,"Olá")); verifyNoInteractions(api);
 }
}
