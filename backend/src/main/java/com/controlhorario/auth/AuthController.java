package com.controlhorario.auth;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

import com.controlhorario.common.web.ValidationException;
import com.controlhorario.user.UserDto;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/auth: login, refresh, logout y registro. No exige access token (la cabecera Authorization
 * se ignora aquí); los filtros de SecurityConfig comprueban antes Origin y rate limit.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final Validator validator;

    public AuthController(AuthService authService, RefreshCookies cookies, Validator validator) {
        this.authService = authService;
        this.cookies = cookies;
        this.validator = validator;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.Session session = authService.login(request.email(), request.password(), ClientInfo.of(http));
        return withCookie(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken, HttpServletRequest http) {
        AuthService.Session session = authService.refresh(refreshToken, ClientInfo.of(http));
        return withCookie(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    /**
     * Con el registro cerrado responde 403 sin mirar el cuerpo; por eso se valida a mano después
     * de comprobarlo, en lugar de con {@code @Valid}.
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody(required = false) RegisterRequest request) {
        authService.ensureRegistrationOpen();
        if (request == null) {
            throw new ValidationException("body", "Faltan los datos del registro");
        }
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    private ResponseEntity<AuthResponse> withCookie(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString())
                .body(session.response());
    }
}
