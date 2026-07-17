package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.util.List;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerJwtConfiguration"
})
@ConditionalOnClass({JwtDecoder.class, JwtAuthenticationProvider.class})
@ConditionalOnSingleCandidate(JwtDecoder.class)
public class SpringJwtTaskEventsAutoConfiguration {
    @Bean
    TaskRealtimeCredentialAuthenticator jwtTaskRealtimeAuthenticator(
            JwtDecoder jwtDecoder,
            ObjectProvider<JwtAuthenticationConverter> converters) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        List<JwtAuthenticationConverter> configuredConverters = converters.orderedStream().toList();
        if (configuredConverters.size() > 1) {
            throw new IllegalStateException("Multiple JwtAuthenticationConverter beans are configured");
        }
        provider.setJwtAuthenticationConverter(configuredConverters.isEmpty()
                ? new JwtAuthenticationConverter()
                : configuredConverters.get(0));
        return new SpringSecurityCredentialAuthenticator(
                provider::authenticate,
                SpringSecurityCredentialAuthenticator.RESOURCE_SERVER_ORDER);
    }
}
