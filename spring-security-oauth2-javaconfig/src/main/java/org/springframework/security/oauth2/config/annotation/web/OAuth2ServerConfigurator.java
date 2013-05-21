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
package org.springframework.security.oauth2.config.annotation.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfiguratorAdapter;
import org.springframework.security.config.annotation.web.ExceptionHandlingConfigurator;
import org.springframework.security.config.annotation.web.ExpressionUrlAuthorizations;
import org.springframework.security.config.annotation.web.HttpBasicConfigurator;
import org.springframework.security.config.annotation.web.HttpConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationManager;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationProcessingFilter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenEndpointFilter;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenGranter;
import org.springframework.security.oauth2.provider.client.ClientDetailsUserDetailsService;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeTokenGranter;
import org.springframework.security.oauth2.provider.code.InMemoryAuthorizationCodeServices;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.oauth2.provider.expression.OAuth2WebSecurityExpressionHandler;
import org.springframework.security.oauth2.provider.implicit.ImplicitTokenGranter;
import org.springframework.security.oauth2.provider.password.ResourceOwnerPasswordTokenGranter;
import org.springframework.security.oauth2.provider.refresh.RefreshTokenGranter;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.ConsumerTokenServices;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.InMemoryTokenStore;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 *
 * @author Rob Winch
 * @since 3.2
 */
public class OAuth2ServerConfigurator
        extends
        SecurityConfiguratorAdapter<DefaultSecurityFilterChain, HttpConfiguration> {
    private AuthenticationEntryPoint authenticationEntryPoint = new OAuth2AuthenticationEntryPoint();
    private AccessDeniedHandler accessDeniedHandler = new OAuth2AccessDeniedHandler();

    private ClientCredentialsTokenEndpointFilter clientCredentialsTokenEndpointFilter;
    private OAuth2AuthenticationProcessingFilter resourcesServerFilter;
    private ClientDetailsService clientDetails;
    private AuthorizationServerTokenServices tokenServices;
    private AuthorizationCodeServices authorizationCodeServices;
    private ResourceServerTokenServices resourceTokenServices;
    private TokenStore tokenStore;
    private TokenGranter tokenGranter;
    private ConsumerTokenServices consumerTokenServices;
    private HttpBasicConfigurator httpBasicConfigurator;
    private String resourceId = "oauth2-resource";
    private SecurityExpressionHandler<FilterInvocation> expressionHandler = new OAuth2WebSecurityExpressionHandler();

    public OAuth2ServerConfigurator clientDetails(ClientDetailsService clientDetails) {
        this.clientDetails = clientDetails;
        return this;
    }

    AuthorizationServerTokenServices getTokenServices() {
        return tokenServices;
    }

    ResourceServerTokenServices getResourceTokenServices() {
        return resourceTokenServices;
    }

    TokenStore getTokenStore() {
        return tokenStore;
    }

    UserDetailsService getUserDetailsService() {
        return new ClientDetailsUserDetailsService(clientDetails);
    }

    private ClientDetailsService clientDetails() {
        return clientDetails;
    }

    @Override
    public void init(HttpConfiguration http) throws Exception {
        if(http.getConfigurator(HttpBasicConfigurator.class) == null) {
            httpBasicConfigurator = new HttpBasicConfigurator();
            httpBasicConfigurator.setBuilder(http);
        }

        http.getConfigurator(ExpressionUrlAuthorizations.class).expressionHandler(expressionHandler);

        http.userDetailsService(getUserDetailsService());

        http.authenticationEntryPoint(authenticationEntryPoint);

        clientCredentialsTokenEndpointFilter = new ClientCredentialsTokenEndpointFilter();
        clientCredentialsTokenEndpointFilter.setAuthenticationManager(http
                .authenticationManager());

        resourcesServerFilter = new OAuth2AuthenticationProcessingFilter();
        resourcesServerFilter
                .setAuthenticationManager(oauthAuthenticationManager(http));
        this.tokenGranter = tokenGranter(http);
        this.consumerTokenServices = consumerTokenServices(http);

        http.
            getConfigurator(ExceptionHandlingConfigurator.class)
                .accessDeniedHandler(accessDeniedHandler);

        if(httpBasicConfigurator != null) {
            httpBasicConfigurator.init(http);
        }
    }

    public OAuth2ServerConfigurator resourceId(String resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    @Override
    public void configure(HttpConfiguration http) throws Exception {
        if(httpBasicConfigurator != null) {
            httpBasicConfigurator.configure(http);
        }
        http
            .addFilterBefore(resourcesServerFilter, AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(clientCredentialsTokenEndpointFilter, BasicAuthenticationFilter.class);

    }

    private AuthenticationManager oauthAuthenticationManager(
            HttpConfiguration http) {
        OAuth2AuthenticationManager oauthAuthenticationManager = new OAuth2AuthenticationManager();
        oauthAuthenticationManager.setResourceId(resourceId);
        oauthAuthenticationManager
                .setTokenServices(resourceTokenServices(http));
        return oauthAuthenticationManager;
    }

    private ResourceServerTokenServices resourceTokenServices(
            HttpConfiguration http) {
        tokenServices(http);
        return this.resourceTokenServices;
    }

    private AuthorizationServerTokenServices tokenServices(HttpConfiguration http) {
        if (tokenServices != null) {
            return tokenServices;
        }
        DefaultTokenServices tokenServices = new DefaultTokenServices();
        tokenServices.setTokenStore(tokenStore());
        tokenServices.setSupportRefreshToken(true);
        tokenServices.setClientDetailsService(clientDetails());
        this.tokenServices = tokenServices;
        this.resourceTokenServices = tokenServices;
        return tokenServices;
    }

    private TokenStore tokenStore() {
        if (tokenStore == null) {
            this.tokenStore = new InMemoryTokenStore();
        }
        return this.tokenStore;
    }

    AuthorizationCodeServices getAuthorizationCodeServices() {
        return authorizationCodeServices;
    }

    private AuthorizationCodeServices authorizationCodeServices(
            HttpConfiguration http) {
        if (authorizationCodeServices == null) {
            authorizationCodeServices = new InMemoryAuthorizationCodeServices();
        }
        return authorizationCodeServices;
    }

    private AuthenticationManager authenticationManager(HttpConfiguration http) {
        return http.authenticationManager();
    }

    TokenGranter getTokenGranter() {
        return tokenGranter;
    }

    ConsumerTokenServices getConsumerTokenServices() {
        return consumerTokenServices;
    }

    private ConsumerTokenServices consumerTokenServices(HttpConfiguration http) {
        if(consumerTokenServices == null) {
            DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
            defaultTokenServices.setClientDetailsService(clientDetails());
            defaultTokenServices.setTokenStore(tokenStore());
            consumerTokenServices = defaultTokenServices;
        }
        return consumerTokenServices;
    }

    private TokenGranter tokenGranter(HttpConfiguration http) throws Exception {
        if(tokenGranter == null) {
            ClientDetailsService clientDetails = clientDetails();
            AuthorizationServerTokenServices tokenServices = tokenServices(http);
            AuthorizationCodeServices authorizationCodeServices = authorizationCodeServices(http);
            AuthenticationManager authenticationManager = authenticationManager(http);

            List<TokenGranter> tokenGranters = new ArrayList<TokenGranter>();
            tokenGranters.add(new AuthorizationCodeTokenGranter(tokenServices,
                    authorizationCodeServices, clientDetails));
            tokenGranters
                    .add(new RefreshTokenGranter(tokenServices, clientDetails));
            tokenGranters
                    .add(new ImplicitTokenGranter(tokenServices, clientDetails));
            tokenGranters.add(new ClientCredentialsTokenGranter(tokenServices,
                    clientDetails));
            tokenGranters.add(new ResourceOwnerPasswordTokenGranter(
                    authenticationManager, tokenServices, clientDetails));
            tokenGranter = new CompositeTokenGranter(tokenGranters);
        }
        return tokenGranter;
    }
}
