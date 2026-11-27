package com.transithub.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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

import jakarta.servlet.http.HttpServletRequest;

/**
 * API security (ADR-0004, ADR-0017).
 *
 * <ul>
 *   <li>Every endpoint except the health check needs a valid access token from
 *       the identity provider (Keycloak, ADR-0017).</li>
 *   <li>Stateless: no server session.</li>
 *   <li>CSRF protection stays <em>on</em>. It is skipped only for requests a
 *       malicious site cannot forge: those carrying a bearer token (browsers
 *       never attach one automatically) and those carrying no cookies at all
 *       (forgery relies on the victim's cookies). Any future cookie-based
 *       request (R-27) is therefore protected without further changes.</li>
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
                .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfiguration::cannotBeForged))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
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
     * A cross-site request forgery rides on cookies the victim's browser sends
     * automatically. A request with a bearer token, or with no cookies at all,
     * cannot be such a forgery.
     */
    static boolean cannotBeForged(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean bearerToken = authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
        boolean noCookies = request.getHeader(HttpHeaders.COOKIE) == null;
        return bearerToken || noCookies;
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
