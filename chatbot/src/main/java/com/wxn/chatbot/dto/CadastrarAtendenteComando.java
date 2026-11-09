package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Atendente;

// Entrada do cadastro de atendente. Credenciais de acesso ficam com a frente de autenticação.
public record CadastrarAtendenteComando(String nome, String email, Atendente.PerfilAcesso perfil) {
}
