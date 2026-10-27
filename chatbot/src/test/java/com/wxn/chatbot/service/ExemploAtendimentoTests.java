package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;
import com.wxn.chatbot.web.PainelHistoricoController;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:exemplo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "painel-local"})
class ExemploAtendimentoTests {
    @Autowired private ExemploAtendimentoService exemplo;
    @Autowired private PainelHistoricoController painel;
    @Autowired private ConversaRepository conversas;

    @Test
    void gravaExemploUmaVezEMostraHistoricoDepoisDoCommit() {
        Long id = exemplo.criar();
        assertEquals(id, exemplo.criar());
        assertEquals(1, conversas.count());
        assertEquals(1, painel.listar().size());
        assertEquals("Marina (exemplo)", painel.listar().getFirst().clienteNome());
        var mensagens = painel.mensagens(id);
        assertEquals(4, mensagens.size());
        assertEquals(Mensagem.TipoRemetente.ATENDENTE, mensagens.get(2).remetente());
        assertEquals("Ana (exemplo)", mensagens.get(2).autor());
        assertEquals(1L, mensagens.get(2).atendenteId());
    }
}
