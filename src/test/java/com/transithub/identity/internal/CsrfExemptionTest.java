package com.transithub.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which requests may skip the CSRF check (see SecurityConfiguration). */
class CsrfExemptionTest {

    @Test
    void bearerTokenRequestsCannotBeForgedEvenWithCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companies");
        request.addHeader("Authorization", "Bearer eyJ...");
        request.setCookies(new Cookie("unrelated", "value"));

        assertTrue(SecurityConfiguration.cannotBeForged(request));
    }

    @Test
    void requestsWithoutCookiesCannotBeForged() {
        assertTrue(SecurityConfiguration.cannotBeForged(new MockHttpServletRequest("POST", "/api/v1/companies")));
    }

    @Test
    void cookieRequestsWithoutABearerTokenMustPassTheCsrfCheck() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companies");
        request.setCookies(new Cookie("SESSION", "abc"));

        assertFalse(SecurityConfiguration.cannotBeForged(request));
    }

    @Test
    void otherAuthorizationSchemesDoNotExempt() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/companies");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        request.setCookies(new Cookie("SESSION", "abc"));

        assertFalse(SecurityConfiguration.cannotBeForged(request));
    }
}
