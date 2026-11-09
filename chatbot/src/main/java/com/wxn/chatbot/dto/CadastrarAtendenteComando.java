package com.wxn.chatbot.dto;

import com.wxn.chatbot.model.Atendente;

public record CadastrarAtendenteComando(String nome, String email, Atendente.PerfilAcesso perfil) {
}
