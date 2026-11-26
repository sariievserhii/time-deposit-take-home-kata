package org.ikigaidigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * API users, bound from {@code app.security.users}. Empty by default, so a deployment without explicitly configured
 * users rejects every API call (fail closed). Passwords are stored encoded, e.g. {@code {bcrypt}$2a$10$...}.
 * Local demo users live only in {@code application-dev.yaml}.
 * <p>
 * Invalid entries fail at startup with a message naming the problem, instead of a bare NullPointerException or an
 * unclear Spring Security error on first use.
 */
@ConfigurationProperties("app.security")
record ApiUsersProperties(List<ApiUser> users) {

    ApiUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    record ApiUser(String name, String password, List<String> roles) {

        ApiUser {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("app.security.users: every user needs a non-blank 'name'");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("app.security.users: user '" + name + "' needs an encoded 'password'"
                        + " such as {bcrypt}$2a$10$...");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalArgumentException("app.security.users: user '" + name + "' needs at least one role"
                        + " (VIEWER, OPERATOR); a user without roles could not call anything");
            }
            roles = List.copyOf(roles);
        }
    }
}
