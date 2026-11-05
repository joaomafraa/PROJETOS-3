package com.wxn.chatbot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wxn.chatbot.model.Lead;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByEmpresaIdOrderByDataCriacaoAscIdAsc(Long empresaId);

    List<Lead> findByEmpresaIdAndContatoOrderByDataCriacaoAscIdAsc(Long empresaId, String contato);

    Optional<Lead> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndContatoAndNecessidadeChave(
            Long empresaId, String contato, String necessidadeChave);
}
