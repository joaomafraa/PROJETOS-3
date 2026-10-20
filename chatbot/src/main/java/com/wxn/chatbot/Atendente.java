package com.wxn.chatbot;
 
import java.util.Objects;
import java.util.regex.Pattern;
 
public class Atendente {

    public enum PerfilAcesso{
        ADMINISTRADOR,
        COORDENADOR,
        ATENDENTE
    }

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Long id;
    private final Empresa empresa;
    private String nome;
    private String email;
    private PerfilAcesso perfil;
    private boolean ativo;
}