package com.bovae.yaj.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.auth.SignupService;
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
    private static final UUID TEST_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Instant TEST_CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final String VALID_REQUEST_JSON =
            """
            {"email":"user@example.com","password":"securePass1"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupService signupService;

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
}
