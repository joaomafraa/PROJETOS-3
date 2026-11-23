
package com.wxn.chatbot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/abertura/**", "/login.html", "/entrar", "/inicio.css", "/login.js", "/csrf-token", "/error").permitAll()
                .requestMatchers("/actuator/health")
                    .permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                .requestMatchers("/api/admin/**")
                    .hasRole("ADMINISTRADOR")
                // Cada nova operacao deve receber uma regra explicita antes de ser exposta.
                .requestMatchers(HttpMethod.GET, "/api/tickets", "/api/tickets/responsaveis",
                        "/api/tickets/{id}", "/api/historico", "/api/historico/fila",
                        "/api/historico/meus", "/api/historico/notificacoes",
                        "/api/historico/{id}/mensagens", "/api/historico/{id}/fontes", "/api/leads", "/api/leads/{id}/conversa",
                        "/api/conversas/{id}/envios", "/api/arquivo-conversas", "/api/arquivo-conversas/itens")
                    .authenticated()
                .requestMatchers(HttpMethod.POST, "/api/tickets/{id}/assumir", "/api/tickets/{id}/concluir",
                        "/api/conversas/{id}/assumir", "/api/conversas/{id}/concluir",
                        "/api/conversas/{id}/mensagens", "/api/leads", "/api/conversas/{id}/envios/{mensagem}/confirmar",
                        "/api/conversas/{id}/envios/{mensagem}/reenviar", "/api/historico/exemplo", "/api/arquivo-conversas/{id}")
                    .authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/arquivo-conversas/{id}").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/leads/{id}/conversa").authenticated()
                .requestMatchers("/painel", "/painel.html", "/painel.css", "/tema.js", "/historico.js", "/contexto.js", "/solicitacoes.js", "/acoes-solicitacao.js", "/leads.js", "/administracao.js", "/fontes.js", "/documentos.js")
                    .authenticated()
                .anyRequest()
                    .denyAll()
            )
            .exceptionHandling(errors -> errors
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .defaultAuthenticationEntryPointFor(new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/entrar"),
                    org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE))
            .formLogin(form -> form.loginPage("/entrar").loginProcessingUrl("/login")
                .defaultSuccessUrl("/painel", true).failureUrl("/entrar?erro").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/entrar?saiu"))
            .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
}
