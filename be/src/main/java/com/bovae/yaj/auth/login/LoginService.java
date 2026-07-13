package com.bovae.yaj.auth.login;

import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.ForbiddenException;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.support.UUIDUtils;
import com.bovae.yaj.web.dto.LoginRequest;
import com.bovae.yaj.web.dto.LoginResponse;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoginService {

    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password.";
    private static final String UNVERIFIED_MSG = "Please verify your email address before logging in.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final long expiresInSeconds;
    private final String dummyHash;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.expiresInSeconds = jwtProperties.tokenTtl().toSeconds();
        // ponytail: dummy hash derived at startup — no credential-shaped string in source
        this.dummyHash = passwordEncoder.encode(UUIDUtils.getRandomUUID());
    }

    public LoginResponse login(LoginRequest request) {
        validateInput(request);

        // validateInput guarantees non-null email and password
        String email = Objects.requireNonNull(request.email()).strip();
        String password = Objects.requireNonNull(request.password());

        loginRateLimiter.checkAndIncrement(email);

        Optional<User> found = userRepository.findByEmail(email);

        boolean isActiveAccount = found.isPresent() && found.get().getDeletedAt() == null;

        if (!isActiveAccount) {
            passwordEncoder.matches(password, dummyHash); // constant-work: equalize timing vs the real-password path
            throw new UnauthorizedException(INVALID_CREDENTIALS_MSG);
        }

        User user = found.get();

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MSG);
        }

        // Correct credentials: the caller is legitimate, so clear the accumulated rate-limit window
        // rather than making a valid user wait it out after a few earlier typos.
        loginRateLimiter.reset(email);

        if (!user.isEmailVerified()) {
            throw new ForbiddenException(UNVERIFIED_MSG);
        }

        String token = jwtService.issue(user.getId());
        LOG.info("Login successful: userId={}", user.getId());
        return LoginResponse.bearer(token, expiresInSeconds);
    }

    private void validateInput(LoginRequest request) {
        if (StringUtils.isBlank(request.email())) {
            throw new ValidationException("An email address is required.");
        }
        if (StringUtils.isBlank(request.password())) {
            throw new ValidationException("A password is required.");
        }
    }
}
