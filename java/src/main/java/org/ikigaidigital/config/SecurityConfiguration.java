package org.ikigaidigital.config;

import org.ikigaidigital.adapter.in.web.TimeDepositApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.List;

/**
 * Role-based access to the two endpoints.
 * <ul>
 *   <li>{@code GET  /api/v1/time-deposits}: role VIEWER. It exposes every customer's balances and withdrawals.</li>
 *   <li>{@code POST /api/v1/time-deposits/balance-updates}: role OPERATOR only. It posts interest to every
 *   deposit, so in production it would belong to the scheduler's service account.</li>
 *   <li>Swagger UI and the OpenAPI document are public, so the contract can be read without credentials.</li>
 *   <li>Everything else is denied.</li>
 * </ul>
 * Authentication is HTTP Basic against configured users (see {@link ApiUsersProperties}), which keeps the kata easy to
 * try from Swagger's Authorize button. The rules are written in terms of roles, so moving to OAuth2/JWT (the
 * production choice, see README) replaces only {@code httpBasic} and the user store, e.g. with
 * {@code oauth2ResourceServer(jwt)} plus a converter from token scopes to roles.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiUsersProperties.class)
class SecurityConfiguration {

    static final String VIEWER = "VIEWER";
    static final String OPERATOR = "OPERATOR";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Spring Boot forwards errors to /error. Permitting it lets a real error status (e.g. 500)
                        // reach the client instead of being masked as 401.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, TimeDepositApi.BASE_PATH).hasRole(VIEWER)
                        .requestMatchers(HttpMethod.POST, TimeDepositApi.BASE_PATH + "/balance-updates").hasRole(OPERATOR)
                        .anyRequest().denyAll())
                // 401 WITHOUT a "WWW-Authenticate: Basic" challenge. With the challenge, a browser that opens the
                // API URL shows its login dialog and then caches the credentials, sending them automatically with
                // later requests, including a hidden cross-site form POST to /balance-updates. Cached Basic
                // credentials behave like a cookie, so that would make CSRF possible. Without the challenge the
                // browser never prompts or caches. API clients (Swagger's Authorize, curl -u, k6) send the
                // Authorization header up front, so they are unaffected. Deliberate deviation from RFC 9110, which
                // expects a challenge on 401.
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Stateless API: credentials on every request, no session and no cookie. With no cookie and no
                // browser-cached credentials (see above), nothing is sent automatically, so there is nothing for
                // CSRF to abuse. That is why CSRF protection is disabled. Checked by ApiSecurityIntegrationTest.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    /** Only configured users. Without any, every API call is rejected (fail closed). */
    @Bean
    UserDetailsService apiUsers(ApiUsersProperties properties) {
        List<UserDetails> users = properties.users().stream()
                .map(user -> User.withUsername(user.name())
                        .password(user.password())
                        .roles(user.roles().toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    /** Delegating encoder: reads the {@code {bcrypt}} prefix, so stored hashes can be migrated to stronger ids later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
