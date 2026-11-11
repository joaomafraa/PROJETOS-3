package com.wxn.chatbot.telegram;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.wxn.chatbot.model.*;
import com.wxn.chatbot.repository.ClienteRepository;

/** Empresa e chatbot devem vir da configuração confiável do canal. */
@Service
public class IdentificacaoTelegramService {
    private final EntityManager em;
    private final ClienteRepository clientes;
    public IdentificacaoTelegramService(EntityManager em, ClienteRepository clientes) { this.em = em; this.clientes = clientes; }

    @Transactional
    public Long obterConversa(Long empresaId, Long chatbotId, long usuarioId, String nome) {
        if (empresaId == null || chatbotId == null || usuarioId <= 0) throw new IllegalArgumentException("Configuração ou usuário inválido.");
        Empresa empresa = em.find(Empresa.class, empresaId, LockModeType.PESSIMISTIC_WRITE);
        Chatbot chatbot = em.find(Chatbot.class, chatbotId);
        if (empresa == null || chatbot == null) throw new IllegalArgumentException("Empresa ou chatbot não encontrado.");
        chatbot.verificarPodeAtender(empresa);
        List<?> existentes = em.createNativeQuery("select conversa_id from telegram_contatos where empresa_id = :empresa and usuario_id = :usuario")
            .setParameter("empresa", empresaId).setParameter("usuario", usuarioId).getResultList();
        if (!existentes.isEmpty()) return ((Number) existentes.get(0)).longValue();
        Long clienteId;
        do { clienteId = clientes.proximoId(); } while (clientes.existsById(clienteId));
        Cliente cliente = Cliente.semTelefone(clienteId);
        if (nome != null && !nome.isBlank()) cliente.alterarNome(nome);
        cliente.vincularEmpresa(empresa);
        em.persist(cliente);
        long conversaId;
        do { conversaId = ((Number) em.createNativeQuery("select nextval('telegram_conversas_id_seq')").getSingleResult()).longValue(); }
        while (em.find(Conversa.class, conversaId) != null);
        em.persist(new Conversa(conversaId, cliente, empresa, chatbot));
        em.flush();
        em.createNativeQuery("insert into telegram_contatos (empresa_id, usuario_id, cliente_id, conversa_id) values (:empresa, :usuario, :cliente, :conversa)")
            .setParameter("empresa", empresaId).setParameter("usuario", usuarioId).setParameter("cliente", clienteId).setParameter("conversa", conversaId).executeUpdate();
        return conversaId;
    }
}
