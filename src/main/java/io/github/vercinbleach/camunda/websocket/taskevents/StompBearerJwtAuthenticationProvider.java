package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "camunda.websocket.task-events.authentication", name = "provider", havingValue = StompBearerJwtAuthenticationProvider.ID)
public class StompBearerJwtAuthenticationProvider implements TaskRealtimeAuthenticationProvider {
    public static final String ID = "stomp-bearer-jwt";
    private static final Pattern BEARER = Pattern.compile("^Bearer ([^\\s]+)$");

    private final JwtDecoder jwtDecoder;
    private final RealtimeProperties properties;
    private final Clock clock;

    @Autowired
    public StompBearerJwtAuthenticationProvider(JwtDecoder jwtDecoder, RealtimeProperties properties) {
        this(jwtDecoder, properties, Clock.systemUTC());
    }

    StompBearerJwtAuthenticationProvider(JwtDecoder jwtDecoder, RealtimeProperties properties, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public TaskRealtimeAuthentication authenticate(StompHeaderAccessor connectHeaders) {
        Jwt jwt = jwtDecoder.decode(extractBearer(connectHeaders));
        validateClaims(jwt);

        String principalClaim = properties.getAuthentication().getJwt().getPrincipalClaim();
        String username = jwt.getClaimAsString(principalClaim);
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("missing configured principal claim");
        }

        return new TaskRealtimeAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username.trim(), null, List.of()),
                jwt.getExpiresAt());
    }

    private String extractBearer(StompHeaderAccessor accessor) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : accessor.toNativeHeaderMap().entrySet()) {
            if ("Authorization".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
        if (values.size() != 1 || values.get(0) == null) {
            throw new IllegalArgumentException("CONNECT requires one Authorization header");
        }
        Matcher matcher = BEARER.matcher(values.get(0).trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("CONNECT Authorization is not Bearer");
        }
        return matcher.group(1);
    }

    private void validateClaims(Jwt jwt) {
        RealtimeProperties.Jwt jwtProperties = properties.getAuthentication().getJwt();
        String issuer = jwtProperties.getIssuer();
        if (StringUtils.hasText(issuer)
                && (jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString()))) {
            throw new IllegalArgumentException("issuer mismatch");
        }
        if (jwt.getAudience() == null || !jwt.getAudience().contains(jwtProperties.getAudience())) {
            throw new IllegalArgumentException("audience mismatch");
        }
        if (!jwtProperties.getAuthorizedParty().equals(jwt.getClaimAsString("azp"))) {
            throw new IllegalArgumentException("authorized party mismatch");
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("expired token");
        }
    }
}
