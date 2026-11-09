
package com.wxn.chatbot.security;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityHttpTests.ControladorTeste.class)
@Import({
        SecurityConfig.class,
        SecurityHttpTests.ControladorTeste.class,
        SecurityHttpTests.UsuariosTeste.class
})
class SecurityHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveBloquearUsuarioNaoAutenticado() throws Exception {
        mockMvc.perform(get("/api/teste/seguro"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devePermitirAtendenteAutenticado() throws Exception {
        mockMvc.perform(get("/api/teste/seguro")
                .with(httpBasic("atendente@teste.com",
                        "SenhaSegura123!")))
                .andExpect(status().isOk());
    }

    @Test
    void deveBloquearAtendenteEmRotaAdministrativa()
            throws Exception {

        mockMvc.perform(get("/api/admin/teste")
                .with(httpBasic("atendente@teste.com",
                        "SenhaSegura123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void devePermitirAdministrador() throws Exception {
        mockMvc.perform(get("/api/admin/teste")
                .with(httpBasic("admin@teste.com",
                        "SenhaSegura123!")))
                .andExpect(status().isOk());
    }

    @Test
    void deveBloquearAtendenteInativo() throws Exception {
        mockMvc.perform(get("/api/teste/seguro")
                .with(httpBasic("inativo@teste.com",
                        "SenhaSegura123!")))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ControladorTeste {

        @GetMapping("/api/teste/seguro")
        public String seguro() {
            return "Acesso autorizado";
        }

        @GetMapping("/api/admin/teste")
        public String administrativo() {
            return "Acesso administrativo autorizado";
        }
    }

    @TestConfiguration
    static class UsuariosTeste {

        @Bean
        UserDetailsService usuariosDeTeste(
                PasswordEncoder encoder) {

            return new InMemoryUserDetailsManager(
                    User.withUsername("atendente@teste.com")
                            .password(encoder.encode("SenhaSegura123!"))
                            .roles("ATENDENTE")
                            .build(),

                    User.withUsername("admin@teste.com")
                            .password(encoder.encode("SenhaSegura123!"))
                            .roles("ADMINISTRADOR")
                            .build(),

                    User.withUsername("inativo@teste.com")
                            .password(encoder.encode("SenhaSegura123!"))
                            .roles("ATENDENTE")
                            .disabled(true)
                            .build()
            );
        }
    }
}
