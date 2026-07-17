package io.github.vercinbleach.camunda.websocket.taskevents;

import jakarta.servlet.FilterRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.ClassUtils;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

final class CamundaRestAuthenticationProviderAdapter implements TaskRealtimeHandshakeAuthenticator, Ordered {
    private final ObjectProvider<AuthenticationProvider> providerBeans;
    private final ObjectProvider<ProcessEngine> processEngines;
    private final AutowireCapableBeanFactory beanFactory;
    private final ObjectProvider<TaskRealtimePrincipalFactory> principalFactories;
    private volatile AuthenticationProvider discoveredProvider;

    CamundaRestAuthenticationProviderAdapter(
            ObjectProvider<AuthenticationProvider> providerBeans,
            ObjectProvider<ProcessEngine> processEngines,
            AutowireCapableBeanFactory beanFactory,
            ObjectProvider<TaskRealtimePrincipalFactory> principalFactories) {
        this.providerBeans = providerBeans;
        this.processEngines = processEngines;
        this.beanFactory = beanFactory;
        this.principalFactories = principalFactories;
    }

    @Override
    public boolean supports(ServerHttpRequest request) {
        return request instanceof ServletServerHttpRequest servletRequest
                && hasAuthorization(request)
                && resolveProvider(servletRequest.getServletRequest()) != null;
    }

    @Override
    public Principal authenticate(ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)) {
            throw new IllegalArgumentException("Camunda REST authentication requires a servlet handshake");
        }
        AuthenticationProvider provider = Objects.requireNonNull(
                resolveProvider(servletRequest.getServletRequest()),
                "No Camunda REST authentication provider is configured");
        ProcessEngine processEngine = resolveProcessEngine();
        HttpServletResponse httpResponse = servletResponse.getServletResponse();
        AuthenticationResult result = provider.extractAuthenticatedUser(
                servletRequest.getServletRequest(), processEngine);
        if (result == null || !result.isAuthenticated()
                || result.getAuthenticatedUser() == null || result.getAuthenticatedUser().isBlank()) {
            provider.augmentResponseByAuthenticationChallenge(httpResponse, processEngine);
            throw new IllegalArgumentException("Camunda REST authentication rejected the handshake");
        }
        return principalFactory().authenticated(result.getAuthenticatedUser());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    private AuthenticationProvider resolveProvider(HttpServletRequest request) {
        List<AuthenticationProvider> beans = providerBeans.orderedStream().toList();
        if (beans.size() > 1) {
            throw new IllegalStateException("Multiple Camunda REST AuthenticationProvider beans are configured");
        }
        if (beans.size() == 1) {
            return beans.get(0);
        }

        List<String> providerClassNames = request.getServletContext().getFilterRegistrations().values().stream()
                .filter(this::isCamundaAuthenticationFilter)
                .map(FilterRegistration::getInitParameters)
                .map(parameters -> parameters.get(ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM))
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (providerClassNames.size() > 1) {
            throw new IllegalStateException("Multiple Camunda REST authentication providers are registered");
        }
        if (providerClassNames.isEmpty()) {
            return null;
        }
        AuthenticationProvider cached = discoveredProvider;
        if (cached != null) {
            return cached;
        }
        return createAndCacheProvider(providerClassNames.get(0));
    }

    private synchronized AuthenticationProvider createAndCacheProvider(String providerClassName) {
        if (discoveredProvider != null) {
            return discoveredProvider;
        }
        try {
            Class<?> providerClass = ClassUtils.forName(providerClassName, getClass().getClassLoader());
            if (!AuthenticationProvider.class.isAssignableFrom(providerClass)) {
                throw new IllegalStateException("Configured Camunda REST provider has the wrong type");
            }
            discoveredProvider = (AuthenticationProvider) beanFactory.createBean(providerClass);
            return discoveredProvider;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Configured Camunda REST provider class was not found", exception);
        }
    }

    private ProcessEngine resolveProcessEngine() {
        List<ProcessEngine> engines = processEngines.orderedStream().toList();
        if (engines.size() == 1) {
            return engines.get(0);
        }
        ProcessEngine defaultEngine = ProcessEngines.getDefaultProcessEngine();
        if (defaultEngine != null) {
            return defaultEngine;
        }
        if (engines.size() > 1) {
            throw new IllegalStateException("No default ProcessEngine is available for WebSocket authentication");
        }
        throw new IllegalStateException("No Camunda ProcessEngine is available");
    }

    private boolean isCamundaAuthenticationFilter(FilterRegistration registration) {
        String className = registration.getClassName();
        if (className == null || className.isBlank()) {
            return false;
        }
        if (ProcessEngineAuthenticationFilter.class.getName().equals(className)) {
            return true;
        }
        try {
            Class<?> filterClass = ClassUtils.forName(className, getClass().getClassLoader());
            return ProcessEngineAuthenticationFilter.class.isAssignableFrom(filterClass);
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private boolean hasAuthorization(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }

    private TaskRealtimePrincipalFactory principalFactory() {
        List<TaskRealtimePrincipalFactory> factories = principalFactories.orderedStream().toList();
        if (factories.size() > 1) {
            throw new IllegalStateException("Multiple TaskRealtimePrincipalFactory beans are configured");
        }
        return factories.isEmpty() ? username -> () -> username : factories.get(0);
    }
}
