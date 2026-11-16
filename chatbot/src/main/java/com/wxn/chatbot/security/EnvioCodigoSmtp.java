package com.wxn.chatbot.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EnvioCodigoSmtp implements EnvioCodigoCliente {
    private final ObjectProvider<JavaMailSender> remetente;
    private final String endereco;
    private final String servidor;

    public EnvioCodigoSmtp(ObjectProvider<JavaMailSender> remetente,
            @Value("${nexus.validacao.email-remetente:}") String endereco,
            @Value("${spring.mail.host:}") String servidor) {
        this.remetente = remetente;
        this.endereco = endereco;
        this.servidor = servidor;
    }

    @Override
    public void enviar(String email, String codigo) {
        JavaMailSender smtp = remetente.getIfAvailable();
        if (smtp == null || servidor.isBlank() || endereco.isBlank()) {
            throw new IllegalStateException("Envio de codigo indisponivel.");
        }
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(endereco);
        mensagem.setTo(email);
        mensagem.setSubject("Codigo de verificacao NEXUS");
        mensagem.setText("Seu codigo de verificacao e " + codigo + ". Ele expira em 10 minutos.");
        smtp.send(mensagem);
    }
}
