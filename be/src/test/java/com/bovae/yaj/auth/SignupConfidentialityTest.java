package com.bovae.yaj.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bovae.yaj.config.properties.SignupProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.web.dto.SignupRequest;
import com.bovae.yaj.web.dto.SignupResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupConfidentialityTest {

    private static final String PASSWORD = "SuperSecret99!";
    private static final String HASH = "$argon2id$v=19$m=16384,t=2,p=1$fakesalt$fakehashvalue";
    private static final String EMAIL = "test@example.com";
    private static final UUID SAVED_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2025-01-15T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupService signupService;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(userRepository, passwordEncoder, new SignupProperties(8, 128, 6, 254));

        serviceLogger = (Logger) LoggerFactory.getLogger(SignupService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    // --- Log confidentiality ---

    @Test
    void signup_shouldNotLogPasswordOrHash_whenSignupSucceeds() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        User saved = new User();
        saved.setId(SAVED_ID);
        saved.setEmail(EMAIL);
        saved.setPasswordHash(HASH);
        saved.setEmailVerified(false);
        saved.setCreatedAt(FIXED_TIMESTAMP);
        saved.setModifiedAt(FIXED_TIMESTAMP);
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenReturn(saved);

        signupService.signup(new SignupRequest(EMAIL, PASSWORD));

        String allLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertFalse(allLogs.contains(PASSWORD), "Logs must not contain the submitted password");
        assertFalse(allLogs.contains(HASH), "Logs must not contain the password hash");
    }

    @Test
    void signup_shouldNotLogPassword_whenValidationFails() {
        String shortPassword = "short";
        try {
            signupService.signup(new SignupRequest(EMAIL, shortPassword));
        } catch (Exception ignored) {
            // Expected — validation failure
        }

        String allLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertFalse(allLogs.contains(shortPassword), "Logs must not contain the submitted password on failure");
    }

    // --- Response confidentiality ---

    @Test
    void signupResponse_shouldNotExposePasswordOrHash_whenSerialized() throws Exception {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        SignupResponse response = new SignupResponse(SAVED_ID, EMAIL, false, FIXED_TIMESTAMP);

        String json = objectMapper.writeValueAsString(response);

        assertFalse(json.contains("password"), "Serialized response must not contain a 'password' field");
        assertFalse(json.contains("hash"), "Serialized response must not contain a 'hash' field");
        assertFalse(json.contains("passwordHash"), "Serialized response must not contain a 'passwordHash' field");
    }
}
