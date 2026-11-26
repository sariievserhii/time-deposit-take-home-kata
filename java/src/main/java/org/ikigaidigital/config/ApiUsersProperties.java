package org.ikigaidigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * API users, bound from {@code app.security.users}. Empty by default, so a deployment without explicitly configured
 * users rejects every API call (fail closed). Passwords are stored encoded, e.g. {@code {bcrypt}$2a$10$...}.
 * Local demo users live only in {@code application-dev.yaml}.
 */
@ConfigurationProperties("app.security")
record ApiUsersProperties(List<ApiUser> users) {

    ApiUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    record ApiUser(String name, String password, List<String> roles) {
    }
}
