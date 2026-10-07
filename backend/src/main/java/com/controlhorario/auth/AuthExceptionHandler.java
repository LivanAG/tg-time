package com.controlhorario.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte {@link AuthException} en ProblemDetail. Va antes que el manejador global (que tiene un
 * {@code @ExceptionHandler(Exception.class)} genérico) y borra la cookie cuando corresponde.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

    private final RefreshCookies cookies;

    AuthExceptionHandler(RefreshCookies cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> handle(AuthException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.isClearCookie()) {
            response.header(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        }
        return response.body(ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()));
    }
}
