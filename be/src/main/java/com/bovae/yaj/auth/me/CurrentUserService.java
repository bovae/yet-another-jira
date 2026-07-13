package com.bovae.yaj.auth.me;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.MeResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final String INVALID_TOKEN_MSG = "Invalid or expired token.";

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public MeResponse me() {
        UUID userId = currentUserProvider.requireCurrentUserId();
        // JwtAuthenticationFilter already rejected deleted/missing users before the request reached a
        // secured endpoint, so a present authentication guarantees an active account. The orElseThrow
        // is a defensive backstop only.
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MSG));
        return MeResponse.from(user);
    }
}
