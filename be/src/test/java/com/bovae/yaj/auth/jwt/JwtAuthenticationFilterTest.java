package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private BearerTokenExtractor bearerTokenExtractor;

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain chain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_shouldSetAuthentication_whenValidBearerToken() throws Exception {
        UUID subject = UUID.randomUUID();
        String rawToken = "valid.jwt.token";
        String header = "Bearer " + rawToken;
        TokenClaims claims =
                new TokenClaims(subject, "jti-1", Instant.now(), Instant.now().plusSeconds(3600));

        when(bearerTokenExtractor.extract(header)).thenReturn(rawToken);
        when(jwtService.validateAccessToken(rawToken)).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", header);
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(bearerTokenExtractor, jwtService);
        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(UsernamePasswordAuthenticationToken.class, auth.getClass());
        assertEquals(subject, auth.getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_shouldPreserveExistingAuthentication_whenAlreadyAuthenticated() throws Exception {
        UUID existingUuid = UUID.randomUUID();
        Authentication existingAuth = new UsernamePasswordAuthenticationToken(existingUuid, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(bearerTokenExtractor, jwtService);
        filter.doFilterInternal(request, response, chain);

        assertSame(existingAuth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(bearerTokenExtractor, jwtService);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest(name = "case=\"{0}\" → no authentication set")
    @MethodSource("failureCases")
    void doFilterInternal_shouldNotSetAuthentication_whenTokenInvalid(
            String description, String headerValue, boolean extractorThrows) throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("Authorization", headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        if (headerValue != null && extractorThrows) {
            when(bearerTokenExtractor.extract(headerValue)).thenThrow(new UnauthorizedException("invalid"));
        }
        if (headerValue != null && !extractorThrows) {
            when(bearerTokenExtractor.extract(headerValue)).thenReturn("some.token");
            when(jwtService.validateAccessToken("some.token")).thenThrow(new UnauthorizedException("expired"));
        }

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(bearerTokenExtractor, jwtService);
        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    static Stream<Arguments> failureCases() {
        return Stream.of(
                Arguments.of("no Authorization header", null, false),
                Arguments.of("blank header", "   ", true),
                Arguments.of("non-bearer scheme", "Basic dXNlcjpwYXNz", true),
                Arguments.of(
                        "validateAccessToken throws (expired/denylisted/malformed)",
                        "Bearer expired.jwt.token",
                        false));
    }
}
