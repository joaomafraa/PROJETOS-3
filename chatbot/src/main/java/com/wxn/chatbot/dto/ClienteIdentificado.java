package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Cliente;

public record ClienteIdentificado(Long id, Long empresaId, String nome, String numeroTelefone,
        boolean identidadeConfirmada, boolean cadastradoAgora) {

    public static ClienteIdentificado de(Cliente cliente, boolean cadastradoAgora) {
        return new ClienteIdentificado(cliente.getId(), cliente.getEmpresa().getId(), cliente.getNome(),
                cliente.getNumeroTelefone(), cliente.isIdentidadeConfirmada(), cadastradoAgora);
    }
}
