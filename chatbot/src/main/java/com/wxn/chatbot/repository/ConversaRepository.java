package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.wxn.chatbot.dto.ConversaResumo;
import com.wxn.chatbot.model.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    boolean existsByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByIdAndEmpresaIdAndStatus(Long id, Long empresaId, Conversa.Status status);

    @Query("""
            select new com.wxn.chatbot.dto.ConversaResumo(
                c.id, c.cliente.id, c.cliente.nome, c.cliente.numeroTelefone,
                c.chatbot.nome, c.dataInicio, c.status)
            from Conversa c
            where c.empresa.id = :empresaId and c.id in :ids
            order by c.id
            """)
    List<ConversaResumo> buscarResumosPorIds(@Param("empresaId") Long empresaId,
            @Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversa c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Conversa> buscarParaAtualizar(
            @Param("id") Long id, @Param("empresaId") Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findByEmpresaIdAndResponsavelIdAndStatusOrderByDataInicioAscIdAsc(Long empresaId, Long responsavelId, Conversa.Status status);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findTop100ByEmpresaIdOrderByDataInicioDescIdDesc(Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "chatbot"})
    List<Conversa> findByEmpresaIdAndStatusOrderByDataInicioAscIdAsc(
            Long empresaId,
            Conversa.Status status);

    @EntityGraph(attributePaths = {"mensagens", "mensagens.atendente", "cliente", "chatbot"})
    Optional<Conversa> findByIdAndEmpresaId(Long id, Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "cliente.empresa", "chatbot", "chatbot.empresa", "responsavel"})
    @Query("select c from Conversa c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Conversa> buscarParaAbrirChamado(
            @Param("id") Long id, @Param("empresaId") Long empresaId);
}
