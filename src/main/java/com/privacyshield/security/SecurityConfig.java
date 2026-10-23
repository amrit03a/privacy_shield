
package com.privacyshield.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            // Current project uses session-based API authentication.
            .csrf(csrf -> csrf.disable())

            // Restore authentication from the existing HTTP session.
            .addFilterBefore(
                new SessionAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            )

            .authorizeHttpRequests(auth -> auth

                // Public pages and static resources
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/register.html",
                    "/dashboard.html",
                    "/workspace.html",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/favicon.ico",
                    "/error"
                ).permitAll()

                // Public login and registration endpoints
                .requestMatchers(
                    "/api/users/login",
                    "/api/users/register",
                    "/api/admin/login"
                ).permitAll()

                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()

                // Allow non-API frontend resources
                .anyRequest().permitAll()
            )

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
