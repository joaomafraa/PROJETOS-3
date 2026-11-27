package com.wxn.chatbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wxn.chatbot.model.PedidoOrcamento;

public interface PedidoOrcamentoRepository extends JpaRepository<PedidoOrcamento, Long> {

    // A empresa faz parte da consulta para nao expor o contato de um pedido de outra empresa.
    Optional<PedidoOrcamento> findByTicketIdAndEmpresaId(Long ticketId, Long empresaId);
}
