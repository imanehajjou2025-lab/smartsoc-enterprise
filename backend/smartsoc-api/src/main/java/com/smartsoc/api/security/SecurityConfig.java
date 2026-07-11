package com.smartsoc.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiSecurityProblemSupport problemSupport;
    private final IngestApiKeyFilter ingestApiKeyFilter;

    @Bean
    // S4502 (CSRF disabled): false positive, same finding already dismissed
    // in CodeQL — stateless bearer-token API, no session cookie, hence no
    // CSRF surface (OWASP CSRF Prevention Cheat Sheet).
    @SuppressWarnings("java:S4502")
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, AbstractAuthenticationToken> jwtConverter)
            throws Exception {
        return http
                // Stateless JWT API: no session, hence no CSRF surface.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**").permitAll()
                        // Webhooks des outils SOC : cle d'API dediee (IngestApiKeyFilter)
                        .requestMatchers("/api/v1/ingest/**").hasRole("INGEST")
                        .anyRequest().authenticated())
                .addFilterBefore(ingestApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Delegating encoder ({bcrypt} prefix): allows a future algorithm
        // migration without breaking stored hashes.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
