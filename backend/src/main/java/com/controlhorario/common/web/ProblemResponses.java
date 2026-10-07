package com.controlhorario.common.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Escribe un ProblemDetail (RFC 7807) desde filtros, donde no llega el @RestControllerAdvice. */
public final class ProblemResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
