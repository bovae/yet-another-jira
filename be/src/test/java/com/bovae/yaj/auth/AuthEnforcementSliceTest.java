package com.bovae.yaj.auth;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.auth.jwt.TokenClaims;
import com.bovae.yaj.auth.login.LoginService;
import com.bovae.yaj.auth.logout.LogoutService;
import com.bovae.yaj.auth.me.CurrentUserService;
import com.bovae.yaj.auth.me.MeResponse;
import com.bovae.yaj.auth.signup.SignupService;
import com.bovae.yaj.auth.verification.EmailVerificationService;
import com.bovae.yaj.auth.verification.VerificationResendService;
import com.bovae.yaj.config.CorsConfig;
import com.bovae.yaj.config.SecurityConfig;
import com.bovae.yaj.config.properties.CorsProperties;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.web.error.ProblemAuthenticationEntryPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ProblemAuthenticationEntryPoint.class, CorsConfig.class})
class AuthEnforcementSliceTest {

    private static final String ME_URL = "/api/v1/auth/me";
    private static final String BOARD_URL = "/api/v1/teams/11111111-1111-1111-1111-111111111111/board";
    private static final String ACTUATOR_INFO_URL = "/actuator/info";
    private static final String ACTUATOR_HEALTH_URL = "/actuator/health";
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BEARER_HEADER = "Bearer " + VALID_TOKEN;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BearerTokenExtractor bearerTokenExtractor;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CurrentUserService currentUserService;

    // SecurityConfig wires the JWT filter, which now looks up the user to reject deleted/missing accounts.
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CorsProperties corsProperties;

    // AuthController dependencies (not exercised but needed for context loading)
    @MockitoBean
    private SignupService signupService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private VerificationResendService verificationResendService;

    @MockitoBean
    private VerificationProperties verificationProperties;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        when(corsProperties.allowedOrigins()).thenReturn(List.of("*"));
        when(corsProperties.allowedMethods()).thenReturn(List.of("GET", "POST", "PUT", "DELETE"));
        when(corsProperties.allowedHeaders()).thenReturn(List.of("*"));
    }

    // --- /api/v1/auth/me without token → 401 problem+json ---

    @Test
    void me_shouldReturn401ProblemJson_whenNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    // --- /api/v1/auth/me with valid token → 200 ---

    @Test
    void me_shouldReturn200_whenValidTokenProvided() throws Exception {
        when(bearerTokenExtractor.extract(BEARER_HEADER)).thenReturn(VALID_TOKEN);
        when(jwtService.validateAccessToken(VALID_TOKEN))
                .thenReturn(new TokenClaims(
                        USER_ID, "jti-1", Instant.now(), Instant.now().plusSeconds(3600)));
        // The filter's active-user check must find a non-deleted user or it rejects the request.
        User activeUser = new User();
        activeUser.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(currentUserService.me()).thenReturn(new MeResponse(USER_ID, "user@example.com", true));

        mockMvc.perform(get(ME_URL).header("Authorization", BEARER_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    // --- protected endpoints without token → 401 ---

    static Stream<String> protectedEndpoints() {
        // The real board path stands in for the deleted mock-board probe: any /api/v1/** path is
        // rejected by default, so an unauthenticated board request proves 401-by-default (E15/D7).
        return Stream.of(ACTUATOR_INFO_URL, BOARD_URL);
    }

    @ParameterizedTest(name = "endpoint={0} without token → 401")
    @MethodSource("protectedEndpoints")
    void protectedEndpoint_shouldReturn401_whenNoToken(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- /actuator/health reachable without token ---

    @Test
    void actuatorHealth_shouldNotReturn401_whenNoToken() throws Exception {
        // In a @WebMvcTest slice, actuator endpoints may not be loaded (→ 404).
        // The key assertion: it is NOT rejected by security (not 401).
        int statusCode = mockMvc.perform(get(ACTUATOR_HEALTH_URL))
                .andReturn()
                .getResponse()
                .getStatus();
        assertNotEquals(401, statusCode, "actuator/health must not be rejected by security");
    }
}
