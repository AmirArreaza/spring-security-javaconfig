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


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.AuthenticationRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * @author Rob Winch
 *
 */
public abstract class WebSecurityConfigurerAdapter implements WebSecurityConfigurer {
    @Autowired
    private ApplicationContext context;

    private final AuthenticationManagerBuilder authenticationBuilder = new AuthenticationManagerBuilder();
    private AuthenticationManagerBuilder parentAuthenticationRegistry = new AuthenticationManagerBuilder();
    private boolean disableAuthenticationRegistry;
    private boolean authenticationManagerInitialized;
    private AuthenticationManager authenticationManager;
    private HttpConfiguration http;
    private boolean disableDefaults;

    protected WebSecurityConfigurerAdapter() {
        this(false);
    }

    protected WebSecurityConfigurerAdapter(boolean disableDefaults) {
        this.disableDefaults = disableDefaults;
    }

    protected void registerAuthentication(AuthenticationRegistry registry) throws Exception {
        this.disableAuthenticationRegistry = true;
    }

    protected HttpConfiguration http() throws Exception {
        if(http != null) {
            return http;
        }
        AuthenticationManager authenticationManager = authenticationManager();
        authenticationBuilder.parentAuthenticationManager(authenticationManager);
        http = new HttpConfiguration(authenticationBuilder);
        http.setSharedObject(UserDetailsService.class, userDetailsService());
        if(!disableDefaults) {
            http
                .exceptionHandling().and()
                .sessionManagement().and()
                .securityContext().and()
                .requestCache().and()
                .anonymous().and()
                .servletApi().and()
                .logout();
        }
        configure(http);
        return http;
    }

    @Bean(name=BeanIds.AUTHENTICATION_MANAGER)
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return new AuthenticationManagerDelegator(authenticationBuilder);
    }

    protected AuthenticationManager authenticationManager() throws Exception {
        if(!authenticationManagerInitialized) {
            registerAuthentication(parentAuthenticationRegistry);
            if(disableAuthenticationRegistry) {
                authenticationManager = getBeanExcluding(AuthenticationManager.class, BeanIds.AUTHENTICATION_MANAGER);
            } else {
                authenticationManagerInitialized = true;
                authenticationManager = parentAuthenticationRegistry.build();
            }
            authenticationManagerInitialized = true;
        }
        return authenticationManager;
    }

    @Bean(name=BeanIds.USER_DETAILS_SERVICE)
    public UserDetailsService userDetailsServiceBean() throws Exception {
        return userDetailsService();
    }

    private UserDetailsService userDetailsService() throws Exception {
        return userDetailsService(parentAuthenticationRegistry);
    }

    protected UserDetailsService userDetailsService(AuthenticationRegistry authenticationRegistry) {
        return authenticationRegistry.userDetailsService();
    }

    @Override
    public void init(WebSecurityConfiguration builder) throws Exception {
        SpringSecurityFilterChainBuilder securityFilterChains = builder.springSecurityFilterChainBuilder();
        securityFilterChains
            .securityFilterChains(http());
    }

    @Override
    public void configure(WebSecurityConfiguration builder) throws Exception {
    }

    private <T> T getBeanExcluding(Class<T> clazz, String beanNameToExclude) {
        String[] beanNames = context.getBeanNamesForType(clazz);
        if(beanNames.length == 1 && !beanNameToExclude.equals(beanNames[0])) {
            return context.getBean(beanNames[0],clazz);
        }
        if(beanNames.length == 2) {
            if(beanNameToExclude.equals(beanNames[0])) {
                return context.getBean(beanNames[1],clazz);
            }
            if(beanNameToExclude.equals(beanNames[1])) {
                return context.getBean(beanNames[0],clazz);
            }
        }
        return null;
    }

    protected abstract void configure(HttpConfiguration http) throws Exception;

    static final class AuthenticationManagerDelegator implements AuthenticationManager {
        private AuthenticationManagerBuilder delegateBuilder;
        private AuthenticationManager delegate;
        private final Object delegateMonitor = new Object();

        AuthenticationManagerDelegator(AuthenticationManagerBuilder authentication) {
            this.delegateBuilder = authentication;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            if(delegate != null) {
                return delegate.authenticate(authentication);
            }

            synchronized(delegateMonitor) {
                if (delegate == null) {
                    delegate = this.delegateBuilder.getObject();
                    this.delegateBuilder = null;
                }
            }

            return delegate.authenticate(authentication);
        }
    }
}