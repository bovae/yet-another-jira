package com.bovae.yaj.auth.jwt;

import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.web.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String UNAVAILABLE_DETAIL =
            "The service is temporarily unable to verify your session. Please retry shortly.";

    private final BearerTokenExtractor bearerTokenExtractor;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticate(header);
            } catch (DataAccessException ex) {
                // The token denylist store (Valkey) is unreachable. Fail closed: the request never
                // proceeds authenticated. This filter runs before the DispatcherServlet, so
                // GlobalExceptionHandler cannot see the exception — write the problem detail directly.
                SecurityContextHolder.clearContext();
                LOG.warn("Token denylist store unreachable during JWT validation; failing closed with 503");
                writeServiceUnavailable(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String header) {
        try {
            String token = bearerTokenExtractor.extract(header);
            TokenClaims claims = jwtService.validateAccessToken(token);
            // A structurally valid token is not enough: the account may have been soft-deleted after
            // the token was issued. Reject deleted/missing users here so no endpoint has to re-check.
            if (!isActiveUser(claims.subject())) {
                SecurityContextHolder.clearContext();
                return;
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(claims.subject(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isActiveUser(UUID userId) {
        return userRepository
                .findById(userId)
                .map(user -> user.getDeletedAt() == null)
                .orElse(false);
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        ProblemDetail problem =
                ProblemDetailFactory.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", UNAVAILABLE_DETAIL);
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
