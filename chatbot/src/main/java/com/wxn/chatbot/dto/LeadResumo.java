package com.wxn.chatbot.dto;

import java.time.Instant;

import com.wxn.chatbot.model.Lead;

// Dados do lead prontos para uso, sem expor a entidade JPA.
public record LeadResumo(Long id, Long empresaId, String nome, Lead.TipoContato tipoContato,
        String contato, Lead.Origem origem, String necessidade, Instant dataCriacao) {

    public static LeadResumo de(Lead lead) {
        return new LeadResumo(lead.getId(), lead.getEmpresa().getId(), lead.getNome(),
                lead.getTipoContato(), lead.getContato(), lead.getOrigem(),
                lead.getNecessidade(), lead.getDataCriacao());
    }
}
