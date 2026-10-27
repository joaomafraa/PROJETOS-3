package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.wxn.chatbot.model.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findTop100ByEmpresaIdOrderByDataInicioDescIdDesc(Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
            Long empresaId,
            Conversa.Status status);

    @EntityGraph(attributePaths = {"mensagens", "mensagens.atendente", "cliente", "chatbot"})
    Optional<Conversa> findByIdAndEmpresaId(Long id, Long empresaId);
}
