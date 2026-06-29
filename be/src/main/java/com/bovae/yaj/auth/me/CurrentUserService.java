package com.bovae.yaj.auth.me;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.auth.jwt.TokenClaims;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.web.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final String INVALID_TOKEN_MSG = "Invalid or expired token.";

    private final BearerTokenExtractor bearerTokenExtractor;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public MeResponse me(@Nullable String authorizationHeader) {
        String token = bearerTokenExtractor.extract(authorizationHeader);
        TokenClaims claims = jwtService.validateAccessToken(token);
        User user = userRepository
                .findById(claims.subject())
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MSG));
        if (user.getDeletedAt() != null) {
            throw new UnauthorizedException(INVALID_TOKEN_MSG);
        }
        return MeResponse.from(user);
    }
}
