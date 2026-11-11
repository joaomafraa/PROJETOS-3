package com.wxn.chatbot.dto;

// Opcao do filtro por responsavel na tela de solicitacoes.
// voce indica o atendente autenticado, para o filtro "Meus tickets".
public record ResponsavelOpcao(Long id, String nome, boolean ativo, boolean voce) {
}
