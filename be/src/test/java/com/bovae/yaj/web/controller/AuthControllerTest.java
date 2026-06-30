package com.bovae.yaj.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.auth.login.LoginService;
import com.bovae.yaj.auth.logout.LogoutService;
import com.bovae.yaj.auth.me.CurrentUserService;
import com.bovae.yaj.auth.signup.SignupService;
import com.bovae.yaj.auth.verification.EmailVerificationService;
import com.bovae.yaj.auth.verification.VerificationResendService;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.error.GoneException;
import com.bovae.yaj.web.dto.MeResponse;
import com.bovae.yaj.web.dto.SignupRequest;
import com.bovae.yaj.web.dto.SignupResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String VERIFY_URL = "/api/v1/auth/verify";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String RESEND_URL = "/api/v1/auth/verification/resend";
    private static final UUID TEST_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Instant TEST_CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final String VALID_REQUEST_JSON =
            """
            {"email":"user@example.com","password":"securePass1"}""";
    private static final String REDIRECT_URL = "https://example.com/login";
    private static final String ERROR_REDIRECT_URL = "https://example.com/verify-error";

    @Autowired
    private MockMvc mockMvc;

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

    @MockitoBean
    private CurrentUserService currentUserService;

    // --- 201 success ---

    @Test
    void signup_shouldReturn201WithJsonBody_whenServiceSucceeds() throws Exception {
        SignupResponse response = new SignupResponse(TEST_ID, "user@example.com", false, TEST_CREATED_AT);
        when(signupService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.id").value(TEST_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.createdAt").value("2025-01-15T10:00:00Z"));
    }

    // --- no password/hash/cookie exposure ---

    @Test
    void signup_shouldNotExposePasswordOrHashOrCookie_whenServiceSucceeds() throws Exception {
        SignupResponse response = new SignupResponse(TEST_ID, "user@example.com", false, TEST_CREATED_AT);
        when(signupService.signup(any(SignupRequest.class))).thenReturn(response);

        MvcResult result = mockMvc.perform(
                        post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("securePass1"), "response body must not contain the submitted password");
    }

    // --- 400 for empty/garbage body ---

    @ParameterizedTest(name = "body=\"{0}\" -> 400 problem+json")
    @ValueSource(strings = {"", "{not valid json"})
    void signup_shouldReturn400ProblemJson_whenBodyUnparseable(String body) throws Exception {
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")));

        verifyNoInteractions(signupService);
    }

    // --- 415 for non-JSON content type ---

    @Test
    void signup_shouldReturn415_whenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.TEXT_PLAIN).content("some text"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(signupService);
    }

    // --- GET /verify → 303 redirect ---

    @Test
    void verifyViaLink_shouldReturn303WithLocationAndNoCookie_whenServiceSucceeds() throws Exception {
        when(verificationProperties.resultRedirectUrl()).thenReturn(REDIRECT_URL);

        mockMvc.perform(get(VERIFY_URL).param("token", "some-token"))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", REDIRECT_URL))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(emailVerificationService).verify("some-token");
    }

    // --- GET /verify failure → 303 redirect to error page ---

    @Test
    void verifyViaLink_shouldRedirectToErrorUrl_whenVerificationFails() throws Exception {
        when(verificationProperties.resultErrorRedirectUrl()).thenReturn(ERROR_REDIRECT_URL);
        doThrow(new GoneException("invalid or expired"))
                .when(emailVerificationService)
                .verify("bad-token");

        mockMvc.perform(get(VERIFY_URL).param("token", "bad-token"))
                .andExpect(status().is(303))
                .andExpect(header().string("Location", ERROR_REDIRECT_URL))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(emailVerificationService).verify("bad-token");
    }

    // --- POST /verify → 200 JSON ---

    @Test
    void verifyPost_shouldReturn200WithJsonBody_whenServiceSucceeds() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                        {"token":"some-token"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.message").value("Your email is verified. Please log in."))
                .andExpect(jsonPath("$.next").value("login"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(emailVerificationService).verify("some-token");
    }

    // --- POST /verification/resend → 202 uniform body ---

    @Test
    void resend_shouldReturn202WithUniformBody_whenServiceCompletes() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                        {"email":"user@example.com"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an unverified account exists for that address,"
                                + " a new verification email has been sent."));

        verify(verificationResendService).resend("user@example.com");
    }

    // --- GET /me → 200 JSON, no sensitive fields ---

    @Test
    void me_shouldReturn200WithUserJson_whenServiceSucceeds() throws Exception {
        var meResponse = new MeResponse(TEST_ID, "user@example.com", true);
        when(currentUserService.me()).thenReturn(meResponse);

        MvcResult result = mockMvc.perform(get(ME_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.id").value(TEST_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"), "response must not contain passwordHash");
        assertFalse(body.contains("\"hash\""), "response must not contain hash field");

        verify(currentUserService).me();
    }
}
