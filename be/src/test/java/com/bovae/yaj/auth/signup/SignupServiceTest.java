package com.bovae.yaj.auth.signup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.verification.VerificationTokenIssuer;
import com.bovae.yaj.config.properties.SignupProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.SignupRequest;
import com.bovae.yaj.web.dto.SignupResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final String RAW_PASSWORD = "securePass1";
    private static final String HASHED_PASSWORD = "$argon2id$v=19$m=16384,t=2,p=1$stubbedhash";
    private static final String MAX_LENGTH_PASSWORD = "a".repeat(128);
    private static final String TOO_LONG_PASSWORD = "a".repeat(129);
    private static final UUID SAVED_ID = UUID.randomUUID();
    private static final Instant SAVED_CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private VerificationTokenIssuer verificationTokenIssuer;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(
                userRepository, passwordEncoder, new SignupProperties(8, 128, 6, 254), verificationTokenIssuer);
    }

    // --- happy path ---

    @Test
    void signup_shouldEncodeOnceWithRawPassword_whenValid() {
        SignupRequest request = new SignupRequest("user@example.com", RAW_PASSWORD);
        stubHappyPath("user@example.com");

        signupService.signup(request);

        verify(passwordEncoder, times(1)).encode(RAW_PASSWORD);
    }

    @Test
    void signup_shouldPersistUnverifiedUserWithHashedPassword_whenValid() {
        SignupRequest request = new SignupRequest("user@example.com", RAW_PASSWORD);
        stubHappyPath("user@example.com");

        SignupResponse response = signupService.signup(request);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertEquals("user@example.com", captured.getEmail());
        assertEquals(HASHED_PASSWORD, captured.getPasswordHash());
        assertFalse(captured.isEmailVerified());

        assertNotNull(response);
        assertEquals(SAVED_ID, response.id());
        assertEquals("user@example.com", response.email());
        assertFalse(response.emailVerified());
        assertEquals(SAVED_CREATED_AT, response.createdAt());
    }

    // --- email normalization ---

    @ParameterizedTest(name = "email=\"{0}\" persists as \"{1}\"")
    @CsvSource({"'  Foo@Bar.com  ', Foo@Bar.com", "'User@Domain.Org', User@Domain.Org"})
    void signup_shouldNormalizeEmail_whenLeadingTrailingWhitespace(String input, String expected) {
        SignupRequest request = new SignupRequest(input, RAW_PASSWORD);
        stubHappyPath(expected);

        signupService.signup(request);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(expected, userCaptor.getValue().getEmail());
    }

    // --- email validation: blank/null ---

    @ParameterizedTest(name = "email=\"{0}\" rejected as blank")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void signup_shouldRejectEmail_whenBlank(String email) {
        SignupRequest request = new SignupRequest(email, RAW_PASSWORD);

        assertThrows(ValidationException.class, () -> signupService.signup(request));

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- email validation: malformed ---

    static Stream<String> malformedEmails() {
        return Stream.of(
                "noatsign",
                "a@",
                "two@@signs.com",
                "@missing-local.com",
                "a@b",
                "a@.b.com",
                "a@b.com.",
                "a@b..c.com",
                "a b@c.d",
                "a@b.c",
                "a" + "@" + "x".repeat(250) + ".com");
    }

    @ParameterizedTest(name = "email=\"{0}\" rejected as malformed")
    @MethodSource("malformedEmails")
    void signup_shouldRejectEmail_whenMalformed(String email) {
        SignupRequest request = new SignupRequest(email, RAW_PASSWORD);

        assertThrows(ValidationException.class, () -> signupService.signup(request));

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- password validation: blank/null ---

    @ParameterizedTest(name = "password=\"{0}\" rejected as blank")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void signup_shouldRejectPassword_whenBlank(String password) {
        SignupRequest request = new SignupRequest("valid@email.com", password);

        assertThrows(ValidationException.class, () -> signupService.signup(request));

        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- password validation: length bounds ---

    static Stream<Arguments> invalidLengthPasswords() {
        return Stream.of(
                Arguments.of("too short (7 chars)", "1234567"),
                Arguments.of("too long (129 chars)", TOO_LONG_PASSWORD));
    }

    @ParameterizedTest(name = "{0} rejected")
    @MethodSource("invalidLengthPasswords")
    void signup_shouldRejectPassword_whenLengthOutsideBounds(String label, String password) {
        SignupRequest request = new SignupRequest("valid@email.com", password);

        assertThrows(ValidationException.class, () -> signupService.signup(request));

        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- password validation: boundary acceptance ---

    static Stream<Arguments> validBoundaryPasswords() {
        return Stream.of(
                Arguments.of("minimum length (8 chars)", "12345678"),
                Arguments.of("whitespace counts toward length", "   a    "),
                Arguments.of("maximum length (128 chars)", MAX_LENGTH_PASSWORD));
    }

    @ParameterizedTest(name = "{0} accepted")
    @MethodSource("validBoundaryPasswords")
    void signup_shouldAcceptPassword_whenWithinLengthBounds(String label, String password) {
        SignupRequest request = new SignupRequest("valid@email.com", password);
        when(userRepository.findByEmail("valid@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(HASHED_PASSWORD);
        User savedUser = buildSavedUser("valid@email.com");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);

        signupService.signup(request);

        verify(userRepository).saveAndFlush(any());
    }

    // --- duplicate pre-check ---

    @ParameterizedTest(name = "duplicate variant=\"{0}\" stripped to \"{1}\"")
    @CsvSource({
        "user@example.com, user@example.com",
        "' USER@example.com ', USER@example.com",
        "' user@EXAMPLE.COM ', user@EXAMPLE.COM"
    })
    void signup_shouldRaiseConflict_whenEmailAlreadyRegistered(String input, String strippedEmail) {
        SignupRequest request = new SignupRequest(input, RAW_PASSWORD);
        when(userRepository.findByEmail(strippedEmail)).thenReturn(Optional.of(new User()));

        assertThrows(ConflictException.class, () -> signupService.signup(request));

        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- race condition: DataIntegrityViolationException ---

    @Test
    void signup_shouldTranslateToConflict_whenUniqueConstraintRace() {
        SignupRequest request = new SignupRequest("race@example.com", RAW_PASSWORD);
        when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        ConflictException ex = assertThrows(ConflictException.class, () -> signupService.signup(request));

        assertEquals("Email address is already registered.", ex.getMessage());
    }

    // --- verification token issuance ---

    @Test
    void signup_shouldInvokeIssuerAfterSave_whenSignupSucceeds() {
        SignupRequest request = new SignupRequest("user@example.com", RAW_PASSWORD);
        stubHappyPath("user@example.com");

        signupService.signup(request);

        verify(verificationTokenIssuer).issue(SAVED_ID, "user@example.com");
    }

    @Test
    void signup_shouldNotPersistUser_whenIssuerFails() {
        SignupRequest request = new SignupRequest("user@example.com", RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        User savedUser = buildSavedUser("user@example.com");
        when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
        RuntimeException issuerFailure = new RuntimeException("token persistence failed");
        org.mockito.Mockito.doThrow(issuerFailure).when(verificationTokenIssuer).issue(SAVED_ID, "user@example.com");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> signupService.signup(request));

        assertEquals(
                "token persistence failed",
                thrown.getMessage(),
                "issuer failure should propagate, triggering @Transactional rollback");
    }

    // --- encoder returns plaintext guard ---

    @Test
    void signup_shouldFailAndNotPersist_whenEncoderReturnsPlaintext() {
        SignupRequest request = new SignupRequest("user@example.com", RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(RAW_PASSWORD);

        assertThrows(IllegalStateException.class, () -> signupService.signup(request));

        verify(userRepository, never()).saveAndFlush(any());
    }

    // --- helpers ---

    private void stubHappyPath(String normalizedEmail) {
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userRepository.saveAndFlush(any())).thenReturn(buildSavedUser(normalizedEmail));
    }

    private User buildSavedUser(String email) {
        User savedUser = new User();
        savedUser.setId(SAVED_ID);
        savedUser.setEmail(email);
        savedUser.setPasswordHash(HASHED_PASSWORD);
        savedUser.setEmailVerified(false);
        savedUser.setCreatedAt(SAVED_CREATED_AT);
        savedUser.setModifiedAt(Instant.parse("2025-01-15T10:00:00Z"));
        return savedUser;
    }
}
