package com.wxn.chatbot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.wxn.chatbot.model.Chatbot;
import com.wxn.chatbot.model.Cliente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Empresa;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversaRepositoryTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ConversaRepository conversaRepository;

    @Test
    void filtraPorEmpresaEStatusEOrdenaPorChegada() {
        Empresa empresa = new Empresa(1L, "WXN");
        Empresa outraEmpresa = new Empresa(2L, "Outra");
        Cliente cliente = new Cliente(1L, "81999999999");
        Chatbot chatbot = new Chatbot(1L, "Assistente", empresa, "Ola");
        Chatbot outroChatbot = new Chatbot(2L, "Outro", outraEmpresa, "Ola");

        entityManager.persist(empresa);
        entityManager.persist(outraEmpresa);
        entityManager.persist(cliente);
        entityManager.persist(chatbot);
        entityManager.persist(outroChatbot);

        Conversa primeira = novaConversaAguardando(1L, cliente, empresa, chatbot);
        Conversa segunda = novaConversaAguardando(2L, cliente, empresa, chatbot);
        Conversa automatica = new Conversa(3L, cliente, empresa, chatbot);
        Conversa deOutraEmpresa = novaConversaAguardando(4L, cliente, outraEmpresa, outroChatbot);

        entityManager.persist(primeira);
        entityManager.persist(segunda);
        entityManager.persist(automatica);
        entityManager.persist(deOutraEmpresa);
        entityManager.flush();

        List<Conversa> fila = conversaRepository.findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
                empresa.getId(),
                Conversa.Status.AGUARDANDO_ATENDENTE);

        assertEquals(List.of(primeira, segunda), fila);
    }

    private Conversa novaConversaAguardando(
            Long id,
            Cliente cliente,
            Empresa empresa,
            Chatbot chatbot) {
        Conversa conversa = new Conversa(id, cliente, empresa, chatbot);
        conversa.solicitarAtendimentoHumano();
        return conversa;
    }
}
