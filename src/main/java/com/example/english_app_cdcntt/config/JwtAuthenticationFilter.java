package com.example.english_app_cdcntt.config;

import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token filter per plan §5.3. Accepts only a parsable {@code type=access} JWT whose
 * {@code uid} matches the loaded DB account; a refresh token presented as Bearer is rejected.
 * A present-but-invalid Bearer header gets the contract 401 body even on permitAll auth
 * endpoints (§4). Only JWT parse failures and unknown accounts map to 401 — infrastructure
 * exceptions (e.g. DB down) propagate and surface as the generic 500, never as credential errors.
 *
 * <p>Deliberately not a Spring bean: {@code SecurityConfig} constructs it inside the filter
 * chain, so the servlet container cannot auto-register it a second time.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    static final String INVALID_TOKEN_MESSAGE = "Token không hợp lệ hoặc hết hạn";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            reject(response);
            return;
        }
        try {
            JwtService.JwtPayload payload = jwtService.parse(token);
            if (payload.type() != TokenType.ACCESS) {
                reject(response);
                return;
            }
            UserPrincipal principal = userDetailsService.loadUserByUsername(payload.username());
            if (!principal.id().equals(payload.userId())) {
                reject(response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | UsernameNotFoundException e) {
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityErrorResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
    }
}
