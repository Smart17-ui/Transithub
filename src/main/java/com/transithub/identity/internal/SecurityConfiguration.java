package com.transithub.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.transithub.shared.web.ProblemCodes;
import com.transithub.shared.web.ProblemResponseWriter;

/**
 * API security (ADR-0004, ADR-0017).
 *
 * <ul>
 *   <li>Every endpoint except the health check needs a valid access token from
 *       the identity provider (Keycloak, ADR-0017).</li>
 *   <li>Stateless: no server session. CSRF protection is off because the
 *       API accepts only bearer tokens, never cookies. (If a cookie-based
 *       session layer is added (R-27), it must turn CSRF protection on.)</li>
 *   <li>Authentication says <em>who</em>; permissions are checked by
 *       {@link com.transithub.identity.Authorization} in application services.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(IdentityProviderProperties.class)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemResponseWriter problems) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            problems.write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
                                    "Sign in and send a valid access token.", ProblemCodes.UNAUTHENTICATED);
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(response, HttpStatus.FORBIDDEN, "Access denied",
                                        "You do not have permission to do this.", ProblemCodes.ACCESS_DENIED)));

        return http.build();
    }

    /**
     * Validates signature (keys fetched lazily from the provider's JWKS
     * endpoint), expiry, issuer, token type and client.
     */
    @Bean
    JwtDecoder jwtDecoder(IdentityProviderProperties provider) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.effectiveJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(provider.issuer()),
                AccessTokenValidators.accessTokensOnly(),
                AccessTokenValidators.issuedToClientIn(provider.clientIds())));
        return decoder;
    }
}
