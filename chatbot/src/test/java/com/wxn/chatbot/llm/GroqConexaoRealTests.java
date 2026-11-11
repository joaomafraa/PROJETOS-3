package com.wxn.chatbot.llm;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="TESTAR_GROQ_REAL",matches="true")
class GroqConexaoRealTests {
 @Autowired GroqProvedor provedor;
 @Test void respondeUsandoSpringAi() {
  String resultado=provedor.responder(List.of(new ProvedorLlm.TrechoConversa("CLIENTE","Responda somente: Olá.")));
  assertNotNull(resultado); assertFalse(resultado.isBlank());
 }
}
