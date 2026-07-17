package io.github.vercinbleach.camunda.websocket.taskevents;

import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.Principal;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamundaRestAuthenticationProviderAdapterTest {
    @Test
    void reusesACamundaRestAuthenticationProviderBean() {
        AuthenticationProvider provider = mock(AuthenticationProvider.class);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        when(provider.extractAuthenticatedUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(processEngine)))
                .thenReturn(AuthenticationResult.successful("camunda-user"));
        CamundaRestAuthenticationProviderAdapter adapter = adapter(provider, processEngine);
        MockHttpServletRequest request = authorizedRequest(mock(ServletContext.class));

        Principal principal = adapter.authenticate(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(principal.getName()).isEqualTo("camunda-user");
    }

    @Test
    void preservesTheCamundaProviderChallengeOnFailure() {
        AuthenticationProvider provider = mock(AuthenticationProvider.class);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        when(provider.extractAuthenticatedUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(processEngine)))
                .thenReturn(AuthenticationResult.unsuccessful());
        CamundaRestAuthenticationProviderAdapter adapter = adapter(provider, processEngine);
        MockHttpServletRequest request = authorizedRequest(mock(ServletContext.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> adapter.authenticate(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provider).augmentResponseByAuthenticationChallenge(
                org.mockito.ArgumentMatchers.same(response), org.mockito.ArgumentMatchers.same(processEngine));
    }

    @Test
    void discoversTheProviderConfiguredOnTheOfficialCamundaRestFilter() {
        FilterRegistration registration = mock(FilterRegistration.class);
        when(registration.getClassName()).thenReturn(ProcessEngineAuthenticationFilter.class.getName());
        when(registration.getInitParameters()).thenReturn(Map.of(
                ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM,
                DiscoveredProvider.class.getName()));
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getFilterRegistrations()).thenAnswer(
                ignored -> Map.of("camunda-auth", registration));
        ProcessEngine processEngine = mock(ProcessEngine.class);
        ObjectProvider<AuthenticationProvider> providers = providerOf();
        ObjectProvider<ProcessEngine> engines = providerOf(processEngine);
        CamundaRestAuthenticationProviderAdapter adapter = new CamundaRestAuthenticationProviderAdapter(
                providers, engines, new DefaultListableBeanFactory(), providerOf());
        MockHttpServletRequest request = authorizedRequest(servletContext);

        Principal principal = adapter.authenticate(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(principal.getName()).isEqualTo("discovered-user");
    }

    @Test
    void ignoresAuthenticationProviderParametersOnUnrelatedFilters() {
        FilterRegistration registration = mock(FilterRegistration.class);
        when(registration.getClassName()).thenReturn("com.example.UnrelatedFilter");
        when(registration.getInitParameters()).thenReturn(Map.of(
                ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM,
                DiscoveredProvider.class.getName()));
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getFilterRegistrations()).thenAnswer(
                ignored -> Map.of("unrelated", registration));
        CamundaRestAuthenticationProviderAdapter adapter = new CamundaRestAuthenticationProviderAdapter(
                providerOf(), providerOf(mock(ProcessEngine.class)), new DefaultListableBeanFactory(), providerOf());
        MockHttpServletRequest request = authorizedRequest(servletContext);

        assertThat(adapter.supports(new ServletServerHttpRequest(request))).isFalse();
    }

    @Test
    void usesTheRegisteredDefaultEngineWhenSeveralEngineBeansExist() {
        AuthenticationProvider provider = mock(AuthenticationProvider.class);
        ProcessEngine defaultEngine = mock(ProcessEngine.class);
        ProcessEngine otherEngine = mock(ProcessEngine.class);
        when(defaultEngine.getName()).thenReturn(ProcessEngines.NAME_DEFAULT);
        when(otherEngine.getName()).thenReturn("other");
        when(provider.extractAuthenticatedUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(defaultEngine)))
                .thenReturn(AuthenticationResult.successful("default-engine-user"));
        ProcessEngines.registerProcessEngine(defaultEngine);
        try {
            CamundaRestAuthenticationProviderAdapter adapter = new CamundaRestAuthenticationProviderAdapter(
                    providerOf(provider), providerOf(defaultEngine, otherEngine),
                    new DefaultListableBeanFactory(), providerOf());

            Principal principal = adapter.authenticate(
                    new ServletServerHttpRequest(authorizedRequest(mock(ServletContext.class))),
                    new ServletServerHttpResponse(new MockHttpServletResponse()));

            assertThat(principal.getName()).isEqualTo("default-engine-user");
        } finally {
            ProcessEngines.unregister(defaultEngine);
        }
    }

    private CamundaRestAuthenticationProviderAdapter adapter(
            AuthenticationProvider provider, ProcessEngine processEngine) {
        return new CamundaRestAuthenticationProviderAdapter(
                providerOf(provider), providerOf(processEngine), new DefaultListableBeanFactory(), providerOf());
    }

    @Test
    void exposesCamundaAuthenticationAsSpringAuthenticationWhenSecurityIsAvailable() {
        AuthenticationProvider provider = mock(AuthenticationProvider.class);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        when(provider.extractAuthenticatedUser(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(processEngine)))
                .thenReturn(AuthenticationResult.successful("spring-security-user"));
        CamundaRestAuthenticationProviderAdapter adapter = new CamundaRestAuthenticationProviderAdapter(
                providerOf(provider), providerOf(processEngine), new DefaultListableBeanFactory(),
                providerOf(new SpringSecurityTaskRealtimePrincipalFactory()));

        Principal principal = adapter.authenticate(
                new ServletServerHttpRequest(authorizedRequest(mock(ServletContext.class))),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(principal).isInstanceOf(org.springframework.security.core.Authentication.class);
        assertThat(((org.springframework.security.core.Authentication) principal).isAuthenticated()).isTrue();
    }

    private MockHttpServletRequest authorizedRequest(ServletContext servletContext) {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v");
        return request;
    }

    @SafeVarargs
    private final <T> ObjectProvider<T> providerOf(T... values) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(ignored -> Stream.of(values));
        return provider;
    }

    public static final class DiscoveredProvider implements AuthenticationProvider {
        @Override
        public AuthenticationResult extractAuthenticatedUser(
                HttpServletRequest request, ProcessEngine processEngine) {
            return AuthenticationResult.successful("discovered-user");
        }

        @Override
        public void augmentResponseByAuthenticationChallenge(
                HttpServletResponse response, ProcessEngine processEngine) {
        }
    }
}
