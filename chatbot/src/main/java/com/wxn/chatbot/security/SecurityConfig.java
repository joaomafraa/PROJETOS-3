
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
                .requestMatchers("/", "/index.html", "/abertura/**", "/login.html", "/inicio.css", "/login.js", "/csrf-token", "/error").permitAll()
                .requestMatchers("/actuator/health")
                    .permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                .requestMatchers("/api/admin/**")
                    .hasRole("ADMINISTRADOR")
                .requestMatchers("/api/**")
                    .authenticated()
                .requestMatchers("/painel.html", "/painel.css", "/tema.js", "/historico.js", "/contexto.js")
                    .authenticated()
                .anyRequest()
                    .denyAll()
            )
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
            .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/login")
                .defaultSuccessUrl("/painel.html", true).failureUrl("/login.html?erro").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login.html?saiu"))
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
