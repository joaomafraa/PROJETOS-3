package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.wxn.chatbot.model.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @EntityGraph(attributePaths = {"empresa", "cliente", "conversaOrigem", "responsavel"})
    Optional<Ticket> findByIdAndEmpresaId(Long id, Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "responsavel"})
    List<Ticket> findByEmpresaIdOrderByDataAberturaAscIdAsc(Long empresaId);
}
