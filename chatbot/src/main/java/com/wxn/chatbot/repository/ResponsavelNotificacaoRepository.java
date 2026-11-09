package com.wxn.chatbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wxn.chatbot.model.ResponsavelNotificacao;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;

public interface ResponsavelNotificacaoRepository extends JpaRepository<ResponsavelNotificacao, Long> {

    @EntityGraph(attributePaths = "atendente")
    List<ResponsavelNotificacao> findByEmpresaIdOrderByTipoAscAtendenteIdAsc(Long empresaId);

    @EntityGraph(attributePaths = "atendente")
    List<ResponsavelNotificacao> findByEmpresaIdAndTipoOrderByAtendenteIdAsc(Long empresaId, TipoNotificacao tipo);

    @Modifying(flushAutomatically = true)
    @Query("delete from ResponsavelNotificacao r where r.empresa.id = :empresaId and r.tipo = :tipo")
    int removerPorEmpresaETipo(@Param("empresaId") Long empresaId, @Param("tipo") TipoNotificacao tipo);
}
