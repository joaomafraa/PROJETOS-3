package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.wxn.chatbot.model.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    @EntityGraph(attributePaths = {"empresa", "cliente", "conversaOrigem", "responsavel"})
    Optional<Ticket> findByIdAndEmpresaId(Long id, Long empresaId);

    @EntityGraph(attributePaths = {"cliente", "responsavel"})
    List<Ticket> findByEmpresaIdOrderByDataAberturaAscIdAsc(Long empresaId);

    // Listagem paginada com filtros (ver TicketSpecifications).
    @Override
    @EntityGraph(attributePaths = {"cliente", "responsavel"})
    Page<Ticket> findAll(Specification<Ticket> criterio, Pageable pagina);
}
