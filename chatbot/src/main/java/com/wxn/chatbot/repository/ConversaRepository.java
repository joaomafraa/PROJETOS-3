package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.wxn.chatbot.model.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversa c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Conversa> buscarParaAtualizar(
            @Param("id") Long id, @Param("empresaId") Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findTop100ByEmpresaIdOrderByDataInicioDescIdDesc(Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
            Long empresaId,
            Conversa.Status status);

    @EntityGraph(attributePaths = {"mensagens", "mensagens.atendente", "cliente", "chatbot"})
    Optional<Conversa> findByIdAndEmpresaId(Long id, Long empresaId);
}
