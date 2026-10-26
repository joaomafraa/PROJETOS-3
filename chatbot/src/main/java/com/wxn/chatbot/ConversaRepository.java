package com.wxn.chatbot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    List<Conversa> findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
            Long empresaId,
            Conversa.Status status);
}
