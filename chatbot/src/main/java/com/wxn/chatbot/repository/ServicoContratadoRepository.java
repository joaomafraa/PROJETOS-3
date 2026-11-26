package com.wxn.chatbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wxn.chatbot.model.ServicoContratado;

public interface ServicoContratadoRepository extends JpaRepository<ServicoContratado, Long> {

    // Sempre filtra por empresa E cliente ao mesmo tempo.
    @Query("""
            select s from ServicoContratado s
            where s.empresa.id = :empresaId and s.cliente.id = :clienteId
            order by case s.situacao when com.wxn.chatbot.model.ServicoContratado.Situacao.ATIVO then 0
                                     when com.wxn.chatbot.model.ServicoContratado.Situacao.SUSPENSO then 1 else 2 end,
                     s.tipo, s.inicio desc, s.id
            """)
    List<ServicoContratado> doCliente(@Param("empresaId") Long empresaId, @Param("clienteId") Long clienteId);
}
