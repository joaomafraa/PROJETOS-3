package com.wxn.chatbot.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wxn.chatbot.model.Atendente;

import jakarta.persistence.LockModeType;

public interface AtendenteRepository extends JpaRepository<Atendente, Long> {

    List<Atendente> findByEmpresaIdOrderByNomeAscIdAsc(Long empresaId);

    List<Atendente> findByEmpresaIdAndAtivoOrderByNomeAscIdAsc(Long empresaId, boolean ativo);

    Optional<Atendente> findByIdAndEmpresaId(Long id, Long empresaId);

    List<Atendente> findByEmpresaIdAndIdIn(Long empresaId, Collection<Long> ids);

    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);

    // Bloqueia a linha para evitar alteracoes administrativas concorrentes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Atendente a where a.id = :id")
    Optional<Atendente> buscarParaAtualizar(@Param("id") Long id);

    @Query(value = "select nextval('atendentes_id_seq')", nativeQuery = true)
    Long proximoId();
}
