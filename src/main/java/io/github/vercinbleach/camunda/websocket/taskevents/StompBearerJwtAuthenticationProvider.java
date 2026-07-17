package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "camunda.websocket.task-events.authentication", name = "provider", havingValue = StompBearerJwtAuthenticationProvider.ID)
public class StompBearerJwtAuthenticationProvider implements TaskRealtimeAuthenticationProvider {
    public static final String ID = "stomp-bearer-jwt";
    private static final Pattern BEARER = Pattern.compile("^Bearer ([^\\s]+)$");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    public StompBearerJwtAuthenticationProvider(
            JwtDecoder jwtDecoder,
            ObjectProvider<JwtAuthenticationConverter> jwtAuthenticationConverters) {
        this(jwtDecoder, jwtAuthenticationConverters.getIfAvailable(JwtAuthenticationConverter::new));
    }

    StompBearerJwtAuthenticationProvider(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.jwtAuthenticationConverter = Objects.requireNonNull(
                jwtAuthenticationConverter,
                "jwtAuthenticationConverter");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public TaskRealtimeAuthentication authenticate(StompHeaderAccessor connectHeaders) {
        Jwt jwt = jwtDecoder.decode(extractBearer(connectHeaders));
        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
        if (authentication == null) {
            throw new IllegalArgumentException("JWT authentication converter returned no authentication");
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw new IllegalArgumentException("JWT must contain an expiration");
        }
        return new TaskRealtimeAuthentication(authentication, expiresAt);
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
}
