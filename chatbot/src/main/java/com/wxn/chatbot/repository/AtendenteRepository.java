package com.wxn.chatbot.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wxn.chatbot.model.Atendente;
import com.wxn.chatbot.model.Atendente.PerfilAcesso;

import jakarta.persistence.LockModeType;

public interface AtendenteRepository extends JpaRepository<Atendente, Long> {

    List<Atendente> findByEmpresaIdOrderByNomeAscIdAsc(Long empresaId);

    List<Atendente> findByEmpresaIdAndAtivoOrderByNomeAscIdAsc(Long empresaId, boolean ativo);

    Optional<Atendente> findByIdAndEmpresaId(Long id, Long empresaId);

    List<Atendente> findByEmpresaIdAndIdIn(Long empresaId, Collection<Long> ids);

    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);

    // Projecao escalar: descobre o tenant confiavel sem anexar uma entidade
    // potencialmente desatualizada ao contexto de persistencia antes dos locks.
    @Query("""
            select a.empresa.id from Atendente a
             where a.id = :id and a.ativo = true and a.perfil = :perfil
            """)
    Optional<Long> buscarEmpresaIdComPerfil(
            @Param("id") Long id, @Param("perfil") PerfilAcesso perfil);

    // Bloqueia somente uma linha da empresa informada. O filtro de empresa faz
    // parte da consulta para que nem a leitura com lock alcance outro tenant.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Atendente a where a.id = :id and a.empresa.id = :empresaId")
    Optional<Atendente> buscarParaAtualizar(
            @Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query(value = "select nextval('atendentes_id_seq')", nativeQuery = true)
    Long proximoId();
}
