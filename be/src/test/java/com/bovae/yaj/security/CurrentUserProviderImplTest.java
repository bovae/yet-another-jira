package com.bovae.yaj.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.error.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserProviderImplTest {

    private final CurrentUserProviderImpl provider = new CurrentUserProviderImpl();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireCurrentUserId_shouldReturnUuid_whenAuthenticatedWithUuidPrincipal() {
        UUID expected = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(expected, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID result = provider.requireCurrentUserId();

        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "should throw UnauthorizedException when authentication={0}")
    @MethodSource("unauthorizedAuthentications")
    void requireCurrentUserId_shouldThrowUnauthorizedException_whenNotAuthenticated(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(UnauthorizedException.class, provider::requireCurrentUserId);
    }

    static Stream<Authentication> unauthorizedAuthentications() {
        return Stream.of(
                null,
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
                new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));
    }
}
