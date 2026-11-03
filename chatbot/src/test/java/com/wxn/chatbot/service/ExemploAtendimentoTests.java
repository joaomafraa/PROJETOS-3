package com.wxn.chatbot.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.model.Conversa;
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
        assertEquals(4, conversas.count());
        assertEquals(1, painel.fila().size());
        assertEquals(id, painel.fila().getFirst().id());
        assertEquals("Marina (exemplo)", painel.fila().getFirst().clienteNome());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_AUTOMATICO, conversas.findById(id + 1).orElseThrow().getStatus());
        assertEquals(Conversa.Status.EM_ATENDIMENTO_HUMANO, conversas.findById(id + 2).orElseThrow().getStatus());
        assertEquals(Conversa.Status.AGUARDANDO_ATENDENTE, conversas.findById(id + 3).orElseThrow().getStatus());
        var mensagens = painel.mensagens(id);
        assertEquals(2, mensagens.size());
        assertEquals(Mensagem.TipoRemetente.CLIENTE, mensagens.get(0).remetente());
        assertEquals(Mensagem.TipoRemetente.CHATBOT, mensagens.get(1).remetente());
        assertEquals(Mensagem.TipoRemetente.ATENDENTE, painel.mensagens(id + 2).get(2).remetente());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> painel.mensagens(id + 3));
    }
}
