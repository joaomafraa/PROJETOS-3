package com.wxn.chatbot.repository;

import com.wxn.chatbot.model.DocumentoConhecimento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoConhecimentoRepository extends JpaRepository<DocumentoConhecimento, Long> {
    List<DocumentoConhecimento> findByEmpresaIdOrderByIdDesc(Long empresaId);
    List<DocumentoConhecimento> findByEmpresaIdAndAtivoTrueOrderByIdDesc(Long empresaId);
    Optional<DocumentoConhecimento> findByIdAndEmpresaId(Long id, Long empresaId);
}
