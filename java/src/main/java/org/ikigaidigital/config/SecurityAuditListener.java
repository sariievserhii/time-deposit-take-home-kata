package org.ikigaidigital.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Security audit log: every failed login, with the attempted username and the client address, so brute-force or
 * credential-stuffing attempts can be detected and alerted on. Passwords are never logged.
 */
@Slf4j
@Component
class SecurityAuditListener {

    private static final int MAX_LOGGED_USERNAME_LENGTH = 64;

    @EventListener
    void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Object details = event.getAuthentication().getDetails();
        String clientAddress = details instanceof WebAuthenticationDetails web ? web.getRemoteAddress() : "unknown";
        log.warn("SECURITY authentication failed: user='{}' client={} reason={}",
                sanitize(event.getAuthentication().getName()), clientAddress,
                event.getException().getClass().getSimpleName());
    }

    /**
     * The username is attacker-controlled. Control characters (CR/LF in particular) are replaced so a crafted
     * username cannot forge extra log lines (log injection), and the length is capped.
     */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() <= MAX_LOGGED_USERNAME_LENGTH ? cleaned : cleaned.substring(0, MAX_LOGGED_USERNAME_LENGTH) + "...";
    }
}
