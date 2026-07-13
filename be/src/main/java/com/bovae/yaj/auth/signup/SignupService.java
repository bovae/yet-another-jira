package com.bovae.yaj.auth.signup;

import com.bovae.yaj.auth.verification.VerificationTokenIssuer;
import com.bovae.yaj.config.properties.SignupProperties;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.ConflictException;
import com.bovae.yaj.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupProperties signupProperties;
    private final VerificationTokenIssuer verificationTokenIssuer;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeAndValidateEmail(request.email());
        String password = validatePassword(request.password());

        if (userRepository.findByEmail(email).isPresent()) {
            LOG.info("Signup rejected: email already registered");
            throw new ConflictException("Email address is already registered.");
        }

        String passwordHash = passwordEncoder.encode(password);
        if (passwordHash.equals(password)) {
            throw new IllegalStateException("Password encoder returned plaintext; refusing to persist.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setEmailVerified(false);

        try {
            User saved = userRepository.saveAndFlush(user);
            verificationTokenIssuer.issue(saved.getId(), saved.getEmail());
            LOG.info("Account created: userId={}", saved.getId());
            return SignupResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Signup race: email unique-constraint rejected the insert; translating to conflict");
            throw new ConflictException("Email address is already registered.");
        }
    }

    private String normalizeAndValidateEmail(@Nullable String submittedEmail) {
        if (StringUtils.isBlank(submittedEmail)) {
            throw new ValidationException("An email address is required.");
        }
        String email = submittedEmail.strip();

        if (email.length() < signupProperties.minEmailLength()
                || email.length() > signupProperties.maxEmailLength()
                || !isWellFormedEmail(email)) {
            throw new ValidationException("The email address is malformed.");
        }
        return email;
    }

    private static boolean isWellFormedEmail(String email) {
        if (email.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        int at = email.indexOf('@');
        if (at < 0 || email.indexOf('@', at + 1) >= 0) {
            return false;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.isEmpty() || domain.isEmpty()) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String validatePassword(@Nullable String submittedPassword) {
        if (StringUtils.isBlank(submittedPassword)) {
            throw new ValidationException("A password is required.");
        }
        if (submittedPassword.length() < signupProperties.minPasswordLength()) {
            throw new ValidationException("The password is too short.");
        }
        if (submittedPassword.length() > signupProperties.maxPasswordLength()) {
            throw new ValidationException("The password is too long.");
        }
        return submittedPassword;
    }
}
