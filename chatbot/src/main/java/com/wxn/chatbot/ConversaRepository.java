package com.wxn.chatbot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

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
