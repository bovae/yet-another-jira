package com.bovae.yaj.auth.verification;

import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import com.bovae.yaj.error.ValidationException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class VerificationResendService {

    private final ResendRateLimiter resendRateLimiter;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationTokenIssuer verificationTokenIssuer;
    private final Clock clock;

    public void resend(@Nullable String submittedEmail) {
        if (submittedEmail == null || submittedEmail.strip().isBlank()) {
            throw new ValidationException("An email address is required.");
        }

        String normalizedEmail = submittedEmail.strip();

        resendRateLimiter.checkAndIncrement(normalizedEmail);

        var userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            return;
        }

        var user = userOpt.get();
        if (user.isEmailVerified()) {
            return;
        }

        verificationTokenRepository.invalidateUnconsumed(
                user.getId(), VerificationPurpose.EMAIL_VERIFICATION, clock.instant());

        verificationTokenIssuer.issue(user.getId(), user.getEmail());
    }
}
