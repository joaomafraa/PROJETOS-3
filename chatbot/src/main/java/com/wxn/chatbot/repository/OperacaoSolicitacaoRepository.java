package com.wxn.chatbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wxn.chatbot.model.OperacaoSolicitacao;

public interface OperacaoSolicitacaoRepository extends JpaRepository<OperacaoSolicitacao, OperacaoSolicitacao.Chave> {
}
