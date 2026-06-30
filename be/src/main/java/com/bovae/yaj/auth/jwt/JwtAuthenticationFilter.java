package com.bovae.yaj.auth.jwt;

import com.bovae.yaj.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final BearerTokenExtractor bearerTokenExtractor;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String header) {
        try {
            String token = bearerTokenExtractor.extract(header);
            TokenClaims claims = jwtService.validateAccessToken(token);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(claims.subject(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
