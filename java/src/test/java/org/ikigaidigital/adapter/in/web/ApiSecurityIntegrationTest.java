package org.ikigaidigital.adapter.in.web;

import org.ikigaidigital.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access rules with real HTTP Basic logins, against the user store as actually configured: the "dev" demo users
 * documented in java/README.md, and no users at all in the default configuration.
 */
class ApiSecurityIntegrationTest {

    private static final String GET_ALL = TimeDepositApi.BASE_PATH;
    private static final String UPDATE = TimeDepositApi.BASE_PATH + "/balance-updates";

    @Nested
    @SpringBootTest(properties = "spring.liquibase.contexts=test")
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @ExtendWith(OutputCaptureExtension.class)
    @Import(TestcontainersConfiguration.class)
    class WithTheDocumentedDemoUsers {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbc;

        @BeforeEach
        void cleanData() {
            jdbc.update("DELETE FROM interest_postings");
            jdbc.update("DELETE FROM withdrawals");
            jdbc.update("DELETE FROM time_deposits");
        }

        /**
         * CSRF protection: a "WWW-Authenticate: Basic" challenge would make a browser show its login dialog and
         * cache the credentials, which it then sends automatically, also with cross-site form POSTs. Without the
         * challenge the browser never caches them.
         */
        @Test
        void anonymousCallsAreRejectedWith401WithoutABasicChallengeThatBrowsersWouldCache() throws Exception {
            mockMvc.perform(get(GET_ALL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
            mockMvc.perform(post(UPDATE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
        }

        @Test
        void wrongPasswordIsRejectedWithoutABasicChallenge(CapturedOutput output) throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("viewer", "not-the-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
            assertThat(output).contains("SECURITY authentication failed: user='viewer'").doesNotContain("not-the-password");
        }

        @Test
        void attackerControlledUsernameCannotForgeLogLines(CapturedOutput output) throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("x\nSECURITY interest run requested by user='admin'", "pw")))
                    .andExpect(status().isUnauthorized());
            assertThat(output).contains("user='x?SECURITY interest run requested by user='admin''")
                    .doesNotContain("\nSECURITY interest run requested by user='admin'");
        }

        @Test
        void interestRunsAreAuditedWithTheCallersName(CapturedOutput output) throws Exception {
            mockMvc.perform(post(UPDATE).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isNoContent());
            assertThat(output).contains("SECURITY interest run requested by user='operator'");
        }

        @Test
        void securityHeadersProtectFinancialData() throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("viewer", "viewer-demo-password")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        void viewerCanListButCannotPostInterest() throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("viewer", "viewer-demo-password")))
                    .andExpect(status().isOk());
            mockMvc.perform(post(UPDATE).with(httpBasic("viewer", "viewer-demo-password")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void operatorCanListAndPostInterest() throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isOk());
            mockMvc.perform(post(UPDATE).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void anythingOutsideTheTwoEndpointsIsDeniedEvenForOperators() throws Exception {
            mockMvc.perform(delete(GET_ALL).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(UPDATE).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/anything-else").with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void swaggerUiAndTheOpenApiContractArePublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.liquibase.contexts=test")
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    class WithoutConfiguredUsers {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void everyApiCallIsRejectedFailClosed() throws Exception {
            mockMvc.perform(get(GET_ALL).with(httpBasic("viewer", "viewer-demo-password")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(UPDATE).with(httpBasic("operator", "operator-demo-password")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void swaggerAndTheApiContractAreNotServedOutsideTheDevProfile() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }
}
