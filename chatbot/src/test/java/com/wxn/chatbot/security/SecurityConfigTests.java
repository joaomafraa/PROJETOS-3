
package com.wxn.chatbot.security;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTests {

    @Test
    void deveGerarHashBCrypt() {
        SecurityConfig config = new SecurityConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        String hash = encoder.encode("SenhaSegura123!");

        assertNotEquals("SenhaSegura123!", hash);
        assertTrue(encoder.matches("SenhaSegura123!", hash));
        assertFalse(encoder.matches("SenhaErrada", hash));
    }
}
