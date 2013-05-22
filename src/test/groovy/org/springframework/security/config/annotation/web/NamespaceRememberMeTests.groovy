/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.security.config.annotation.web

import org.springframework.security.config.annotation.authentication.AuthenticationRegistry
import org.springframework.security.config.annotation.provisioning.InMemoryUserDetailsManagerSecurityBuilder;

import static org.springframework.security.config.annotation.web.util.RequestMatchers.*;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.config.annotation.BaseSpringSpec
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpRequestResponseHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter
import org.springframework.security.web.jaasapi.JaasApiIntegrationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.security.web.util.AntPathRequestMatcher
import org.springframework.security.web.util.AnyRequestMatcher;
import org.springframework.security.web.util.RequestMatcher
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests to verify that all the functionality of <anonymous> attributes is present
 *
 * @author Rob Winch
 *
 */
public class NamespaceRememberMeTests extends BaseSpringSpec {
    FilterChainProxy springSecurityFilterChain
    MockHttpServletRequest request
    MockHttpServletResponse response
    MockFilterChain chain

    def setup() {
        request = new MockHttpServletRequest()
        response = new MockHttpServletResponse()
        chain = new MockFilterChain()
    }

    def "http/remember-me"() {
        setup:
            loadConfig(RememberMeConfig)
            springSecurityFilterChain = context.getBean(FilterChainProxy)
        when: "login with remember me"
            setup()
            request.requestURI = "/login"
            request.method = "POST"
            request.parameters.username = ["user"] as String[]
            request.parameters.password = ["password"] as String[]
            request.parameters.'remember-me' = ["true"] as String[]
            springSecurityFilterChain.doFilter(request,response,chain)
            Cookie rememberMeCookie = getRememberMeCookie()
        then: "response contains remember me cookie"
            rememberMeCookie != null
        when: "session expires"
            setup()
            request.setCookies(rememberMeCookie)
            request.requestURI = "/abc"
            springSecurityFilterChain.doFilter(request,response,chain)
            MockHttpSession session = request.getSession()
        then: "initialized to RememberMeAuthenticationToken"
            SecurityContext context = new HttpSessionSecurityContextRepository().loadContext(new HttpRequestResponseHolder(request, response))
            context.getAuthentication() instanceof RememberMeAuthenticationToken
        when: "logout"
            setup()
            request.setSession(session)
            request.setCookies(rememberMeCookie)
            request.requestURI = "/logout"
            springSecurityFilterChain.doFilter(request,response,chain)
            rememberMeCookie = getRememberMeCookie()
        then: "logout cookie expired"
            response.getRedirectedUrl() == "/login?logout"
            rememberMeCookie.maxAge == 0
        when: "use remember me after logout"
            setup()
            request.setCookies(rememberMeCookie)
            request.requestURI = "/abc"
            springSecurityFilterChain.doFilter(request,response,chain)
        then: "sent to default login page"
            response.getRedirectedUrl() == "http://localhost/login"
    }

    @Configuration
    static class RememberMeConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .authorizeUrls()
                    .antMatchers("/**").hasRole("USER")
                    .and()
                .formLogin()
                    .and()
                .rememberMe()
        }
    }

    def "http/remember-me@services-ref"() {
        setup:
            RememberMeServicesRefConfig.REMEMBER_ME_SERVICES = Mock(RememberMeServices)
        when: "use custom remember-me services"
            loadConfig(RememberMeServicesRefConfig)
        then: "custom remember-me services used"
            findFilter(RememberMeAuthenticationFilter).rememberMeServices == RememberMeServicesRefConfig.REMEMBER_ME_SERVICES
            findFilter(UsernamePasswordAuthenticationFilter).rememberMeServices == RememberMeServicesRefConfig.REMEMBER_ME_SERVICES
    }

    @Configuration
    static class RememberMeServicesRefConfig extends BaseWebConfig {
        static RememberMeServices REMEMBER_ME_SERVICES
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .rememberMeServices(REMEMBER_ME_SERVICES)
        }
    }

    def "http/remember-me@authentication-success-handler-ref"() {
        setup:
            AuthSuccessConfig.SUCCESS_HANDLER = Mock(AuthenticationSuccessHandler)
        when: "use custom success handler"
            loadConfig(AuthSuccessConfig)
        then: "custom remember-me success handler is used"
            findFilter(RememberMeAuthenticationFilter).successHandler == AuthSuccessConfig.SUCCESS_HANDLER
    }

    @Configuration
    static class AuthSuccessConfig extends BaseWebConfig {
        static AuthenticationSuccessHandler SUCCESS_HANDLER
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .authenticationSuccessHandler(SUCCESS_HANDLER)
        }
    }

    // http/remember-me@data-source-ref is not supported directly. Instead use http/remember-me@token-repository-ref example

    def "http/remember-me@key"() {
        when: "use custom key"
            loadConfig(KeyConfig)
            AuthenticationManager authManager = context.getBean(AuthenticationManager)
        then: "custom key services used"
            findFilter(RememberMeAuthenticationFilter).rememberMeServices.key == "KeyConfig"
            authManager.authenticate(new RememberMeAuthenticationToken("KeyConfig", "user", AuthorityUtils.createAuthorityList("ROLE_USER")))
    }

    @Configuration
    static class KeyConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .key("KeyConfig")
        }
    }

    // http/remember-me@services-alias is not supported use standard aliasing instead

    def "http/remember-me@token-repository-ref"() {
        setup:
            TokenRepositoryRefConfig.TOKEN_REPOSITORY = Mock(PersistentTokenRepository)
        when: "use custom token services"
            loadConfig(TokenRepositoryRefConfig)
        then: "custom token services used with PersistentTokenBasedRememberMeServices"
            PersistentTokenBasedRememberMeServices rememberMeServices = findFilter(RememberMeAuthenticationFilter).rememberMeServices
            findFilter(RememberMeAuthenticationFilter).rememberMeServices.tokenRepository == TokenRepositoryRefConfig.TOKEN_REPOSITORY
    }

    @Configuration
    static class TokenRepositoryRefConfig extends BaseWebConfig {
        static PersistentTokenRepository TOKEN_REPOSITORY
        protected void configure(HttpConfiguration http) throws Exception {
            // JdbcTokenRepositoryImpl tokenRepository = new JdbcTokenRepositoryImpl()
            // tokenRepository.setDataSource(dataSource);
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .tokenRepository(TOKEN_REPOSITORY)
        }
    }

    def "http/remember-me@token-validity-seconds"() {
        when: "use token validity"
            loadConfig(TokenValiditySecondsConfig)
        then: "custom token validity used"
            findFilter(RememberMeAuthenticationFilter).rememberMeServices.tokenValiditySeconds == 1
    }

    @Configuration
    static class TokenValiditySecondsConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .tokenValiditySeconds(1)
        }
    }

    def "http/remember-me@token-validity-seconds default"() {
        when: "use token validity"
            loadConfig(DefaultTokenValiditySecondsConfig)
        then: "custom token validity used"
            findFilter(RememberMeAuthenticationFilter).rememberMeServices.tokenValiditySeconds == AbstractRememberMeServices.TWO_WEEKS_S
    }

    @Configuration
    static class DefaultTokenValiditySecondsConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
        }
    }

    def "http/remember-me@use-secure-cookie"() {
        when: "use secure cookies = true"
            loadConfig(UseSecureCookieConfig)
        then: "secure cookies will be used"
            ReflectionTestUtils.getField(findFilter(RememberMeAuthenticationFilter).rememberMeServices, "useSecureCookie") == true
    }

    @Configuration
    static class UseSecureCookieConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .useSecureCookie(true)
        }
    }

    def "http/remember-me@use-secure-cookie defaults"() {
        when: "use secure cookies not specified"
            loadConfig(DefaultUseSecureCookieConfig)
        then: "secure cookies will be null (use secure if the request is secure)"
            ReflectionTestUtils.getField(findFilter(RememberMeAuthenticationFilter).rememberMeServices, "useSecureCookie") == null
    }

    @Configuration
    static class DefaultUseSecureCookieConfig extends BaseWebConfig {
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
        }
    }

    def "http/remember-me defaults UserDetailsService with custom UserDetailsService"() {
        setup:
            DefaultsUserDetailsServiceWithDaoConfig.USERDETAILS_SERVICE = Mock(UserDetailsService)
        when: "use secure cookies not specified"
            loadConfig(DefaultsUserDetailsServiceWithDaoConfig)
        then: "RememberMeServices defaults to the custom UserDetailsService"
            ReflectionTestUtils.getField(findFilter(RememberMeAuthenticationFilter).rememberMeServices, "userDetailsService") == DefaultsUserDetailsServiceWithDaoConfig.USERDETAILS_SERVICE
    }

    @Configuration
    @EnableWebSecurity
    static class DefaultsUserDetailsServiceWithDaoConfig extends WebSecurityConfigurerAdapter {
        static UserDetailsService USERDETAILS_SERVICE

        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
        }

        protected void registerAuthentication(
                AuthenticationRegistry authenticationRegistry) throws Exception {
            authenticationRegistry
                    .userDetailsService(USERDETAILS_SERVICE);
        }
    }

    def "http/remember-me@user-service-ref"() {
        setup:
            UserServiceRefConfig.USERDETAILS_SERVICE = Mock(UserDetailsService)
        when: "use custom UserDetailsService"
            loadConfig(UserServiceRefConfig)
        then: "custom UserDetailsService is used"
            ReflectionTestUtils.getField(findFilter(RememberMeAuthenticationFilter).rememberMeServices, "userDetailsService") == UserServiceRefConfig.USERDETAILS_SERVICE
    }

    @Configuration
    static class UserServiceRefConfig extends BaseWebConfig {
        static UserDetailsService USERDETAILS_SERVICE
        protected void configure(HttpConfiguration http) throws Exception {
            http
                .formLogin()
                    .and()
                .rememberMe()
                    .userDetailsService(USERDETAILS_SERVICE)
        }
    }


    Cookie getRememberMeCookie(String cookieName="remember-me") {
        response.getCookie(cookieName)
    }
}
