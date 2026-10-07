package com.controlhorario.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.controlhorario.common.web.ProblemResponses;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protección CSRF de /api/auth/{login,register,refresh,logout} (POST), además de SameSite=Strict:
 * la cabecera Origin debe estar en APP_ALLOWED_ORIGINS. Si falta Origin se usa el origen del
 * Referer; si faltan los dos, 403.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    static final String FORBIDDEN_ORIGIN = "Origen no permitido";

    private final Set<String> allowedOrigins;

    public OriginCheckFilter(Collection<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? Set.of() : allowedOrigins.stream()
                .map(OriginCheckFilter::originOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(AuthPaths.isPost(request) && AuthPaths.ORIGIN_CHECKED.contains(AuthPaths.of(request)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = requestOrigin(request);
        if (origin == null || !allowedOrigins.contains(origin)) {
            ProblemResponses.write(response, HttpStatus.FORBIDDEN, FORBIDDEN_ORIGIN);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Origen normalizado de la petición: el de Origin o, si falta, el del Referer. */
    static String requestOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            return originOf(origin);
        }
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer != null && !referer.isBlank()) {
            return originOf(referer);
        }
        return null;
    }

    /**
     * {@code esquema://host[:puerto]} en minúsculas y sin el puerto por defecto; {@code null} si no
     * es una URL http(s) absoluta (por ejemplo, el Origin "null" de un iframe con sandbox).
     */
    static String originOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443);
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
