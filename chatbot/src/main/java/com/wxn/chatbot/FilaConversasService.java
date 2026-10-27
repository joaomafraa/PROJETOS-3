package com.wxn.chatbot;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilaConversasService {

    private final ConversaRepository conversaRepository;

    public FilaConversasService(ConversaRepository conversaRepository) {
        this.conversaRepository = Objects.requireNonNull(
                conversaRepository,
                "O repositorio de conversas e obrigatorio.");
    }

    @Transactional(readOnly = true)
    public List<ConversaResumo> visualizarFila(Atendente atendente) {
        Objects.requireNonNull(atendente, "O atendente e obrigatorio.");
        if (!atendente.isAtivo()) {
            throw new SecurityException("Um atendente inativo nao pode visualizar a fila.");
        }

        return conversaRepository.findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
                atendente.getEmpresa().getId(),
                Conversa.Status.AGUARDANDO_ATENDENTE).stream()
                .map(conversa -> new ConversaResumo(conversa.getId(), conversa.getCliente().getId(),
                        conversa.getCliente().getNome(), conversa.getCliente().getNumeroTelefone(),
                        conversa.getChatbot().getNome(), conversa.getDataInicio(), conversa.getStatus()))
                .toList();
    }
}
