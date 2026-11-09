package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Atendente;

// Dados do atendente para administração, sem expor a entidade JPA.
public record AtendenteResumo(Long id, Long empresaId, String nome, String email,
        Atendente.PerfilAcesso perfil, boolean ativo) {

    public static AtendenteResumo de(Atendente atendente) {
        return new AtendenteResumo(atendente.getId(), atendente.getEmpresa().getId(),
                atendente.getNome(), atendente.getEmail(), atendente.getPerfil(), atendente.isAtivo());
    }
}
