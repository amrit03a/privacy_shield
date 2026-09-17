package com.privacyshield.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SessionAuthenticationFilter sessionAuthenticationFilter() {
        return new SessionAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth

                        // =========================
                        // PUBLIC PAGES
                        // =========================
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/dashboard.html",
                                "/workspace.html",
                                "/admin.html",
                                "/admin-dashboard.html",
                                "/css/**",
                                "/js/**",

                                // Database test endpoint
                                "/test-db",

                                // Error page
                                "/error"
                        ).permitAll()


                        // =========================
                        // PUBLIC AUTH APIs
                        // =========================
                        .requestMatchers(
                                "/api/users/register",
                                "/api/users/login",
                                "/api/admin/login"
                        ).permitAll()


                        // =========================
                        // EVERYTHING ELSE
                        // =========================
                        .anyRequest().authenticated()
                )

                // Disable Spring's default login page.
                .formLogin(AbstractHttpConfigurer::disable)

                // Disable HTTP Basic authentication.
                .httpBasic(AbstractHttpConfigurer::disable)

                // Restore authentication from our HTTP session.
                .addFilterBefore(
                        sessionAuthenticationFilter,
                        AuthorizationFilter.class
                );

        return http.build();
    }
}