package com.bovae.yaj.auth.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.ForbiddenException;
import com.bovae.yaj.error.RateLimitException;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.LoginRequest;
import com.bovae.yaj.web.dto.LoginResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String DUMMY_HASH = "$argon2id$dummy";
    private static final String STORED_HASH = "$argon2id$v=19$m=16384,t=2,p=1$storedhash";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final long EXPIRES_IN_SECONDS = TOKEN_TTL.toSeconds();
    private static final String ISSUED_TOKEN = "eyJ.test.token";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        JwtProperties jwtProperties = new JwtProperties(
                "this-is-a-test-secret-that-is-at-least-32-characters-long", TOKEN_TTL, 5, Duration.ofMinutes(15));
        loginService = new LoginService(userRepository, passwordEncoder, jwtService, jwtProperties, loginRateLimiter);
    }

    // --- success case ---

    @Test
    void login_shouldIssueToken_whenVerifiedActiveAccountWithCorrectPassword() {
        User user = buildVerifiedActiveUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", STORED_HASH)).thenReturn(true);
        when(jwtService.issue(USER_ID)).thenReturn(ISSUED_TOKEN);

        LoginResponse response = loginService.login(new LoginRequest("user@example.com", "correctPass"));

        assertEquals(ISSUED_TOKEN, response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(EXPIRES_IN_SECONDS, response.expiresInSeconds());
        verify(jwtService, times(1)).issue(USER_ID);
    }

    // --- 403: unverified account ---

    @Test
    void login_shouldThrowForbidden_whenCorrectPasswordButUnverified() {
        User user = buildUnverifiedActiveUser();
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", STORED_HASH)).thenReturn(true);

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> loginService.login(new LoginRequest("unverified@example.com", "correctPass")));

        assertEquals("Please verify your email address before logging in.", ex.getMessage());
        verify(jwtService, never()).issue(any());
    }

    // --- uniform 401 branches (parametrized) ---

    @ParameterizedTest(name = "login returns uniform 401: {0}")
    @MethodSource("uniform401Cases")
    void login_shouldThrowUnauthorizedWithUniformMessage_whenCredentialsInvalid(String caseName) {
        LoginRequest request = stubFor401Case(caseName);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> loginService.login(request));

        assertEquals(INVALID_CREDENTIALS_MSG, ex.getMessage());
        verify(jwtService, never()).issue(any());
    }

    static Stream<Arguments> uniform401Cases() {
        return Stream.of(
                Arguments.of("unknown email"), Arguments.of("wrong password"), Arguments.of("soft-deleted account"));
    }

    private LoginRequest stubFor401Case(String caseName) {
        if ("unknown email".equals(caseName)) {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(eq("anyPass"), eq(DUMMY_HASH))).thenReturn(false);
            return new LoginRequest("nobody@example.com", "anyPass");
        } else if ("wrong password".equals(caseName)) {
            User user = buildVerifiedActiveUser();
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", STORED_HASH)).thenReturn(false);
            return new LoginRequest("user@example.com", "wrongPass");
        } else if ("soft-deleted account".equals(caseName)) {
            User user = buildSoftDeletedUser();
            when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq("anyPass"), eq(DUMMY_HASH))).thenReturn(false);
            return new LoginRequest("deleted@example.com", "anyPass");
        }
        throw new IllegalArgumentException("Unknown case: " + caseName);
    }

    // --- validation: blank/empty credentials ---

    @ParameterizedTest(name = "login rejects blank email: \"{0}\"")
    @MethodSource("blankEmails")
    void login_shouldThrowValidationException_whenEmailBlank(String email) {
        assertThrows(ValidationException.class, () -> loginService.login(new LoginRequest(email, "somePassword")));

        verifyNoInteractions(userRepository);
    }

    static Stream<String> blankEmails() {
        return Stream.of(null, "", "   ", "\t\n");
    }

    @ParameterizedTest(name = "login rejects empty password: \"{0}\"")
    @MethodSource("emptyPasswords")
    void login_shouldThrowValidationException_whenPasswordEmpty(String password) {
        assertThrows(
                ValidationException.class, () -> loginService.login(new LoginRequest("user@example.com", password)));

        verifyNoInteractions(userRepository);
    }

    static Stream<String> emptyPasswords() {
        return Stream.of(null, "");
    }

    // --- email normalization (trimmed lookup) ---

    @Test
    void login_shouldTrimEmail_whenLeadingTrailingWhitespace() {
        User user = buildVerifiedActiveUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", STORED_HASH)).thenReturn(true);
        when(jwtService.issue(USER_ID)).thenReturn(ISSUED_TOKEN);

        loginService.login(new LoginRequest("  user@example.com  ", "correctPass"));

        verify(userRepository).findByEmail("user@example.com");
    }

    // --- constant-work timing defense ---

    @Test
    void login_shouldThrowRateLimitException_whenRateLimitExceeded() {
        doThrow(new RateLimitException("Too many login attempts. Please try again later.", 900))
                .when(loginRateLimiter)
                .checkAndIncrement("user@example.com");

        assertThrows(
                RateLimitException.class, () -> loginService.login(new LoginRequest("user@example.com", "anyPass")));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_shouldCallMatchesOnceWithDummyHash_whenNoActiveAccount() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("somePass"), eq(DUMMY_HASH))).thenReturn(false);

        assertThrows(
                UnauthorizedException.class,
                () -> loginService.login(new LoginRequest("nobody@example.com", "somePass")));

        verify(passwordEncoder, times(1)).matches("somePass", DUMMY_HASH);
    }

    @Test
    void login_shouldCallMatchesOnceWithDummyHash_whenSoftDeletedAccount() {
        User user = buildSoftDeletedUser();
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("somePass"), eq(DUMMY_HASH))).thenReturn(false);

        assertThrows(
                UnauthorizedException.class,
                () -> loginService.login(new LoginRequest("deleted@example.com", "somePass")));

        verify(passwordEncoder, times(1)).matches("somePass", DUMMY_HASH);
    }

    // --- helpers ---

    private User buildVerifiedActiveUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");
        user.setPasswordHash(STORED_HASH);
        user.setEmailVerified(true);
        user.setDeletedAt(null);
        return user;
    }

    private User buildUnverifiedActiveUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("unverified@example.com");
        user.setPasswordHash(STORED_HASH);
        user.setEmailVerified(false);
        user.setDeletedAt(null);
        return user;
    }

    private User buildSoftDeletedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("deleted@example.com");
        user.setPasswordHash(STORED_HASH);
        user.setEmailVerified(true);
        user.setDeletedAt(Instant.parse("2025-01-10T00:00:00Z"));
        return user;
    }
}
