package io.github.vercinbleach.camunda.websocket.taskevents;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.util.List;

@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerOpaqueTokenConfiguration"
})
@ConditionalOnClass({OpaqueTokenIntrospector.class, OpaqueTokenAuthenticationProvider.class})
@ConditionalOnSingleCandidate(OpaqueTokenIntrospector.class)
public class SpringOpaqueTokenTaskEventsAutoConfiguration {
    @Bean
    TaskRealtimeCredentialAuthenticator opaqueTokenTaskRealtimeAuthenticator(
            OpaqueTokenIntrospector introspector,
            ObjectProvider<OpaqueTokenAuthenticationConverter> converters) {
        OpaqueTokenAuthenticationProvider provider = new OpaqueTokenAuthenticationProvider(introspector);
        List<OpaqueTokenAuthenticationConverter> configuredConverters = converters.orderedStream().toList();
        if (configuredConverters.size() > 1) {
            throw new IllegalStateException("Multiple OpaqueTokenAuthenticationConverter beans are configured");
        }
        if (configuredConverters.size() == 1) {
            provider.setAuthenticationConverter(configuredConverters.get(0));
        }
        return new SpringSecurityCredentialAuthenticator(
                provider::authenticate,
                SpringSecurityCredentialAuthenticator.RESOURCE_SERVER_ORDER);
    }
}
