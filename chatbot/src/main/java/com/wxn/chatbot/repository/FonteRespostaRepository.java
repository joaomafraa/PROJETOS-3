package com.wxn.chatbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wxn.chatbot.model.FonteResposta;

public interface FonteRespostaRepository extends JpaRepository<FonteResposta, Long> {

    // A empresa faz parte de toda consulta para nao expor fontes de outra empresa.
    List<FonteResposta> findByEmpresaIdAndConversaIdOrderByMensagemIdAscUsoAscOrdemAsc(
            Long empresaId, Long conversaId);

    List<FonteResposta> findByEmpresaIdAndConversaIdAndMensagemIdAndUsoOrderByOrdemAsc(
            Long empresaId, Long conversaId, Long mensagemId, FonteResposta.Uso uso);

    boolean existsByConversaIdAndMensagemId(Long conversaId, Long mensagemId);
}
