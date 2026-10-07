package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginCheckFilterTest {

    private final OriginCheckFilter filter =
            new OriginCheckFilter(List.of("https://Horas.Example.com/", "http://localhost:5173", " "));

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest post(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    @Test
    void allowsConfiguredOriginsIgnoringCaseTrailingSlashAndDefaultPort() throws Exception {
        for (String origin : List.of("https://horas.example.com", "https://HORAS.example.com:443",
                "http://localhost:5173")) {
            MockHttpServletRequest request = post("/api/auth/login");
            request.addHeader(HttpHeaders.ORIGIN, origin);
            assertThat(run(request).getStatus()).as(origin).isEqualTo(200);
        }
    }

    @Test
    void rejectsForeignMissingOrOpaqueOrigins() throws Exception {
        for (String origin : List.of("https://evil.example", "http://horas.example.com", "https://horas.example.com:8443",
                "https://horas.example.com.evil.example", "null", "file:///etc/passwd", "no es una url")) {
            MockHttpServletRequest request = post("/api/auth/refresh");
            request.addHeader(HttpHeaders.ORIGIN, origin);
            MockHttpServletResponse response = run(request);
            assertThat(response.getStatus()).as(origin).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("Origen no permitido");
        }
        assertThat(run(post("/api/auth/logout")).getStatus()).isEqualTo(403);
    }

    @Test
    void fallsBackToTheRefererOnlyWhenOriginIsMissing() throws Exception {
        MockHttpServletRequest allowed = post("/api/auth/register");
        allowed.addHeader(HttpHeaders.REFERER, "https://horas.example.com/registro?x=1");
        assertThat(run(allowed).getStatus()).isEqualTo(200);

        MockHttpServletRequest foreign = post("/api/auth/register");
        foreign.addHeader(HttpHeaders.REFERER, "https://evil.example/horas.example.com");
        assertThat(run(foreign).getStatus()).isEqualTo(403);

        MockHttpServletRequest both = post("/api/auth/login");
        both.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        both.addHeader(HttpHeaders.REFERER, "https://horas.example.com/login");
        assertThat(run(both).getStatus()).isEqualTo(403);
    }

    @Test
    void onlyChecksPostsToTheSessionEndpoints() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/api/auth/login")).getStatus()).isEqualTo(200);
        assertThat(run(post("/api/workdays/2026-10-07")).getStatus()).isEqualTo(200);
        assertThat(run(post("/api/me/password")).getStatus()).isEqualTo(200);

        MockHttpServletRequest withContextPath = post("/app/api/auth/login");
        withContextPath.setContextPath("/app");
        assertThat(run(withContextPath).getStatus()).isEqualTo(403);
    }

    @Test
    void emptyConfigurationRejectsEverything() throws Exception {
        OriginCheckFilter closed = new OriginCheckFilter(null);
        MockHttpServletRequest request = post("/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        closed.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void originNormalization() {
        assertThat(OriginCheckFilter.originOf("HTTPS://Example.COM:443/path")).isEqualTo("https://example.com");
        assertThat(OriginCheckFilter.originOf("http://example.com:80")).isEqualTo("http://example.com");
        assertThat(OriginCheckFilter.originOf("http://localhost:5173/")).isEqualTo("http://localhost:5173");
        assertThat(OriginCheckFilter.originOf("null")).isNull();
        assertThat(OriginCheckFilter.originOf("ftp://example.com")).isNull();
        assertThat(OriginCheckFilter.originOf(null)).isNull();
    }
}
