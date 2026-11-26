package com.wxn.chatbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wxn.chatbot.model.PreferenciaAgendamento;

public interface PreferenciaAgendamentoRepository extends JpaRepository<PreferenciaAgendamento, Long> {

    // A empresa faz parte da consulta para nao expor pedidos de outra empresa.
    Optional<PreferenciaAgendamento> findByTicketIdAndEmpresaId(Long ticketId, Long empresaId);
}
