package com.bovae.yaj.web.controller;

import com.bovae.yaj.auth.login.LoginService;
import com.bovae.yaj.auth.logout.LogoutService;
import com.bovae.yaj.auth.me.CurrentUserService;
import com.bovae.yaj.auth.signup.SignupService;
import com.bovae.yaj.auth.verification.EmailVerificationService;
import com.bovae.yaj.auth.verification.VerificationResendService;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.error.GoneException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.web.dto.LoginRequest;
import com.bovae.yaj.web.dto.LoginResponse;
import com.bovae.yaj.web.dto.MeResponse;
import com.bovae.yaj.web.dto.ResendRequest;
import com.bovae.yaj.web.dto.ResendResponse;
import com.bovae.yaj.web.dto.SignupRequest;
import com.bovae.yaj.web.dto.SignupResponse;
import com.bovae.yaj.web.dto.VerifyRequest;
import com.bovae.yaj.web.dto.VerifyResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final EmailVerificationService emailVerificationService;
    private final VerificationResendService verificationResendService;
    private final VerificationProperties verificationProperties;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final CurrentUserService currentUserService;

    @PostMapping(
            value = "/signup",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@RequestBody SignupRequest request) {
        return signupService.signup(request);
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyViaLink(@RequestParam(name = "token", required = false) @Nullable String token) {
        URI redirect;
        try {
            emailVerificationService.verify(token);
            redirect = URI.create(verificationProperties.resultRedirectUrl());
        } catch (ValidationException | GoneException ex) {
            // A browser following an emailed link must land on a human-readable page, not a JSON
            // problem body, so a bad/expired token redirects to the configured error screen. The
            // POST endpoint still surfaces these as 400/410 for API clients. The transaction has
            // already rolled back by the time the exception reaches here.
            redirect = URI.create(verificationProperties.resultErrorRedirectUrl());
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(redirect).build();
    }

    @PostMapping(
            value = "/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        emailVerificationService.verify(request.token());
        return VerifyResponse.success();
    }

    @PostMapping(
            value = "/verification/resend",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResendResponse resend(@RequestBody ResendRequest request) {
        verificationResendService.resend(request.email());
        return ResendResponse.uniform();
    }

    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) @Nullable String authorization) {
        logoutService.logout(authorization);
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public MeResponse me() {
        return currentUserService.me();
    }
}
