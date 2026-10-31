package com.careslot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Configuration
    @ConditionalOnExpression("'${careslot.demo-mode:false}' == 'true'")
    static class LocalDemo {
        @Bean SecurityFilterChain demoSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**").permitAll().anyRequest().permitAll())
                .build();
        }
    }

    @Configuration
    @ConditionalOnExpression("'${careslot.demo-mode:false}' != 'true' and '${careslot.jwt-issuer-uri:}'.length() > 0")
    static class JwtEnabled {
        @Bean JwtDecoder jwtDecoder(@Value("${careslot.jwt-issuer-uri}") String issuer) {
            return JwtDecoders.fromIssuerLocation(issuer);
        }
        @Bean SecurityFilterChain jwtSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())).build();
        }
    }

    @Configuration
    @ConditionalOnExpression("'${careslot.demo-mode:false}' != 'true' and '${careslot.jwt-issuer-uri:}'.length() == 0")
    static class AuthNotConfigured {
        @Bean SecurityFilterChain lockedSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**").permitAll().anyRequest().denyAll())
                .build();
        }
    }
}
