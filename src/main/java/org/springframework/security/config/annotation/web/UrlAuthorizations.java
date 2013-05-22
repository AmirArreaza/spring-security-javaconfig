/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.config.annotation.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.web.access.intercept.DefaultFilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.security.web.util.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 *
 * @author Rob Winch
 * @since 3.2
 */
public class UrlAuthorizations extends BaseInterceptUrlConfigurator<UrlAuthorizations.AuthorizedUrl> {

    public UrlAuthorizations interceptUrl(RequestMatcher requestMatcher, String... configAttributes) {
        return interceptUrl(Arrays.asList(requestMatcher), SecurityConfig.createList(configAttributes));
    }

    public UrlAuthorizations interceptUrl(Iterable<? extends RequestMatcher> requestMatchers, String... configAttributes) {
        return interceptUrl(requestMatchers, SecurityConfig.createList(configAttributes));
    }

    public UrlAuthorizations interceptUrl(Iterable<? extends RequestMatcher> requestMatchers, Collection<ConfigAttribute> configAttributes) {
        for(RequestMatcher requestMatcher : requestMatchers) {
            addMapping(new UrlMapping(requestMatcher, configAttributes));
        }
        return this;
    }

    @Override
    final List<AccessDecisionVoter> decisionVoters() {
        List<AccessDecisionVoter> decisionVoters = new ArrayList<AccessDecisionVoter>();
        decisionVoters.add(new RoleVoter());
        decisionVoters.add(new AuthenticatedVoter());
        return decisionVoters;
    }

    @Override
    FilterInvocationSecurityMetadataSource createMetadataSource() {
        return new DefaultFilterInvocationSecurityMetadataSource(createRequestMap());
    }

    public class AuthorizedUrl {
        private List<RequestMatcher> requestMatchers;

        private AuthorizedUrl(List<RequestMatcher> requestMatchers) {
            this.requestMatchers = requestMatchers;
        }

        public UrlAuthorizations hasRole(String role) {
            return access(UrlAuthorizations.hasRole(role));
        }

        public UrlAuthorizations hasAnyRole(String role) {
            return access(UrlAuthorizations.hasAnyRole(role));
        }

        public UrlAuthorizations hasAuthority(String authority) {
            return access(UrlAuthorizations.hasAuthority(authority));
        }

        public UrlAuthorizations hasAnyAuthority(String... authorities) {
            return access(UrlAuthorizations.hasAnyAuthority(authorities));
        }

        public UrlAuthorizations anonymous() {
            return hasRole("ROLE_ANONYMOUS");
        }

        public UrlAuthorizations access(String... attributes) {
            interceptUrl(requestMatchers, SecurityConfig.createList(attributes));
            return UrlAuthorizations.this;
        }
    }

    @Override
    AuthorizedUrl chainRequestMatchers(List<RequestMatcher> requestMatchers) {
        return new AuthorizedUrl(requestMatchers);
    }

    /**
     * @param role
     * @return
     */
    public static String hasRole(String role) {
        return "ROLE_" + role;
    }

    /**
     * @param role
     * @return
     */
    public static String hasAnyRole(String... roles) {
        return "'ROLE_" + StringUtils.arrayToDelimitedString(roles, "','ROLE_") + "'";
    }


    public static String hasAuthority(String authority) {
        return "hasAuthority('" + authority + "')";
    }

    public static String hasAnyAuthority(String... authorities) {
        String anyAuthorities = StringUtils.arrayToDelimitedString(authorities, "','");
        return "'" + anyAuthorities + "'";
    }
}
