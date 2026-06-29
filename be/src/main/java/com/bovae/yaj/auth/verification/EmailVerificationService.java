package com.bovae.yaj.auth.verification;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import com.bovae.yaj.error.GoneException;
import com.bovae.yaj.error.ValidationException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String UNIFORM_MESSAGE =
            "This verification link is invalid or has expired. Please request a new verification email.";

    private final TokenHasher tokenHasher;
    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public void verify(@Nullable String rawToken) {
        if (StringUtils.isBlank(rawToken)) {
            throw new ValidationException("A verification token is required.");
        }

        String hash = tokenHasher.hash(rawToken);

        VerificationToken token = verificationTokenRepository
                .findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new GoneException(UNIFORM_MESSAGE));

        if (!VerificationPurpose.EMAIL_VERIFICATION.equals(token.getPurpose())) {
            throw new GoneException(UNIFORM_MESSAGE);
        }

        if (!clock.instant().isBefore(token.getExpiresAt())) {
            throw new GoneException(UNIFORM_MESSAGE);
        }

        if (token.getConsumedAt() != null) {
            throw new GoneException(UNIFORM_MESSAGE);
        }

        token.setConsumedAt(clock.instant());

        var user = userRepository.getReferenceById(token.getUserId());
        user.setEmailVerified(true);
    }
}
