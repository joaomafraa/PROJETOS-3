package com.wxn.chatbot.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wxn.chatbot.model.NotificacaoChamado;
import com.wxn.chatbot.model.ResponsavelNotificacao.TipoNotificacao;

// Todas as consultas do painel filtram por empresa E destinatario:
// ninguem le ou marca o aviso de outra pessoa.
public interface NotificacaoChamadoRepository extends JpaRepository<NotificacaoChamado, Long> {

    boolean existsByTicketIdAndDestinatarioIdAndTipo(Long ticketId, Long atendenteId, TipoNotificacao tipo);

    @EntityGraph(attributePaths = {"ticket", "ticket.cliente"})
    List<NotificacaoChamado> findTop50ByEmpresaIdAndDestinatarioIdOrderByCriadaEmDescIdDesc(
            Long empresaId, Long atendenteId);

    @EntityGraph(attributePaths = {"ticket", "ticket.cliente"})
    List<NotificacaoChamado> findTop50ByEmpresaIdAndDestinatarioIdAndLidaEmIsNullOrderByCriadaEmDescIdDesc(
            Long empresaId, Long atendenteId);

    long countByEmpresaIdAndDestinatarioIdAndLidaEmIsNull(Long empresaId, Long atendenteId);

    Optional<NotificacaoChamado> findByIdAndEmpresaIdAndDestinatarioId(Long id, Long empresaId, Long atendenteId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificacaoChamado n set n.lidaEm = :agora
            where n.empresa.id = :empresaId and n.destinatario.id = :atendenteId and n.lidaEm is null
            """)
    int marcarTodasComoLidas(@Param("empresaId") Long empresaId, @Param("atendenteId") Long atendenteId,
            @Param("agora") Instant agora);

    // Chamados recentes que ainda nao geraram nenhum aviso (o processamento falhou
    // ou a empresa ainda nao tinha responsavel). Usado pelo reprocessamento.
    @Query("""
            select t.id from Ticket t
            where t.dataAbertura >= :desde
              and not exists (select n.id from NotificacaoChamado n where n.ticket = t)
            order by t.id
            """)
    List<Long> chamadosSemNotificacaoDesde(@Param("desde") Instant desde);
}
