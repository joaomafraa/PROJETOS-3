package com.wxn.chatbot.service;

import com.wxn.chatbot.dto.AtendimentoHumanoResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.repository.ConversaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtendimentoHumanoService {
    private final ConversaRepository conversas;
    private final EntityManager entityManager;

    public AtendimentoHumanoService(ConversaRepository conversas, EntityManager entityManager) {
        this.conversas = conversas;
        this.entityManager = entityManager;
    }

    /** Caso de uso interno: empresa e conversa devem vir do contexto validado do canal. */
    @Transactional
    public AtendimentoHumanoResumo solicitar(Long conversaId, Long empresaId) {
        validarId(conversaId, "conversa");
        validarId(empresaId, "empresa");
        Conversa conversa = buscar(conversaId, empresaId);
        conversa.solicitarAtendimentoHumano();
        return resumo(conversa);
    }

    /** O atendenteId deve vir da identidade autenticada, nunca livremente da requisição. */
    @Transactional
    public AtendimentoHumanoResumo assumir(Long conversaId, Long atendenteId) {
        validarId(conversaId, "conversa");
        validarId(atendenteId, "atendente");
        Atendente atendente = entityManager.find(Atendente.class, atendenteId);
        if (atendente == null || !atendente.isAtivo()) {
            throw new SecurityException("É necessário um atendente cadastrado e ativo.");
        }
        Conversa conversa = buscar(conversaId, atendente.getEmpresa().getId());
        conversa.iniciarAtendimentoHumano(atendente);
        return resumo(conversa);
    }

    private Conversa buscar(Long id, Long empresaId) {
        return conversas.buscarParaAtualizar(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada nesta empresa."));
    }

    private static AtendimentoHumanoResumo resumo(Conversa conversa) {
        Long responsavelId = null;
        if (conversa.getResponsavel() != null) responsavelId = conversa.getResponsavel().getId();
        return new AtendimentoHumanoResumo(conversa.getId(), conversa.getStatus(), responsavelId);
    }

    private static void validarId(Long id, String campo) {
        if (id == null || id <= 0) throw new IllegalArgumentException("O id de " + campo + " deve ser positivo.");
    }
}
