package org.ikigaidigital.config;

import org.ikigaidigital.config.ApiUsersProperties.ApiUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiUsersPropertiesTest {

    private static final String HASH = "{bcrypt}$2a$10$6sw1yvKXIdINDsSnwzryju7KYtHqDEF7lJ.dkvt7mndJlH2JRV5nO";

    @Test
    void noConfiguredUsersMeansAnEmptyListFailClosed() {
        assertThat(new ApiUsersProperties(null).users()).isEmpty();
    }

    @Test
    void invalidUsersFailAtStartupWithAClearMessage() {
        assertThatThrownBy(() -> new ApiUser(" ", HASH, List.of("VIEWER")))
                .hasMessageContaining("non-blank 'name'");
        assertThatThrownBy(() -> new ApiUser("viewer", null, List.of("VIEWER")))
                .hasMessageContaining("user 'viewer' needs an encoded 'password'");
        assertThatThrownBy(() -> new ApiUser("viewer", HASH, null))
                .hasMessageContaining("user 'viewer' needs at least one role");
    }
}
