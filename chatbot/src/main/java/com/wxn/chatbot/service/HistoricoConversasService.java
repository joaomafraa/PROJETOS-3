package com.wxn.chatbot.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wxn.chatbot.dto.MensagemResumo;
import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Conversa;
import com.wxn.chatbot.model.Mensagem;
import com.wxn.chatbot.repository.ConversaRepository;

@Service
public class HistoricoConversasService {
    private final ConversaRepository conversaRepository;

    public HistoricoConversasService(ConversaRepository conversaRepository) {
        this.conversaRepository = Objects.requireNonNull(conversaRepository);
    }

    @Transactional(readOnly = true)
    public List<MensagemResumo> visualizarHistorico(Long conversaId, Atendente atendente) {
        Objects.requireNonNull(atendente, "O atendente e obrigatorio.");
        if (!atendente.isAtivo()) {
            throw new SecurityException("Um atendente inativo nao pode visualizar o historico.");
        }
        if (conversaId == null || conversaId <= 0) {
            throw new IllegalArgumentException("O id da conversa deve ser positivo.");
        }
        Conversa conversa = conversaRepository.findByIdAndEmpresaId(
                conversaId, atendente.getEmpresa().getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversa nao encontrada nesta empresa."));
        return conversa.getMensagens().stream().map(mensagem -> resumir(mensagem, conversa)).toList();
    }

    private MensagemResumo resumir(Mensagem mensagem, Conversa conversa) {
        String autor;
        Long atendenteId = null;
        if (mensagem.getAtendente() != null) {
            autor = mensagem.getAtendente().getNome();
            atendenteId = mensagem.getAtendente().getId();
        } else if (mensagem.getRemetente() == Mensagem.TipoRemetente.CHATBOT) {
            autor = conversa.getChatbot().getNome();
        } else {
            autor = conversa.getCliente().getNome();
            if (autor == null) {
                autor = "Cliente";
            }
        }
        return new MensagemResumo(mensagem.getId(), mensagem.getConteudo(), mensagem.getDataEnvio(),
                mensagem.getRemetente(), atendenteId, autor);
    }
}
