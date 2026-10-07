package com.controlhorario.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.controlhorario.common.security.ClientIp;
import com.controlhorario.common.web.ProblemResponses;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** 429 con Retry-After al pasar de N peticiones por minuto, por IP y endpoint, en login, register y refresh. */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final String TOO_MANY_REQUESTS = "Demasiados intentos. Espera un momento y vuelve a intentarlo.";

    private final AuthRateLimiter limiter;

    public AuthRateLimitFilter(AuthRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(AuthPaths.isPost(request) && AuthPaths.RATE_LIMITED.contains(AuthPaths.of(request)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = limiter.tryConsume(AuthPaths.of(request) + "|" + ClientIp.of(request));
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            ProblemResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}
