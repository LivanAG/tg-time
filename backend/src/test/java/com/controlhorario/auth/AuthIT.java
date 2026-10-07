package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;

import com.controlhorario.user.Role;
import com.controlhorario.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends AuthTestSupport {

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    RefreshTokenCleanup cleanup;

    // ------------------------------------------------------------------ login

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        User user = createUser(Role.ADMIN);

        MvcResult result = login(user.getEmail().toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.name").value("Persona de prueba"))
                .andExpect(jsonPath("$.user.company").value("IZERTIS"))
                .andExpect(jsonPath("$.user.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String cookie = setCookieHeader(result);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "Max-Age=604800", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Secure"); // perfil dev: APP_COOKIE_SECURE=false
        String refreshToken = refreshCookie(result);
        assertThat(refreshToken).hasSize(43).matches("[A-Za-z0-9_-]+");

        // El access token sirve para la API.
        String accessToken = accessToken(result);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Claims: sub, role, iat, exp = iat + 15 min, jti; firmado con HS256.
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));

        // En base de datos solo está el SHA-256 del refresh token, con navegador e IP.
        List<RefreshToken> stored = refreshTokens.findByUserId(user.getId());
        assertThat(stored).hasSize(1);
        RefreshToken token = stored.get(0);
        assertThat(token.getTokenHash()).isEqualTo(RefreshTokens.hash(refreshToken)).isNotEqualTo(refreshToken);
        assertThat(token.getFamilyId()).isNotNull();
        assertThat(token.getIp()).isEqualTo("127.0.0.1");
        assertThat(token.getRevokedAt()).isNull();
        assertThat(Duration.between(clock.instant(), token.getExpiresAt())).isEqualTo(Duration.ofDays(7));

        assertThat(auditActions(user.getId())).contains("LOGIN_OK");
    }

    @Test
    void loginStoresTruncatedUserAgent() throws Exception {
        User user = createUser(Role.USER);
        mvc.perform(authPost("/api/auth/login")
                        .header(HttpHeaders.USER_AGENT, "x".repeat(400))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk());

        assertThat(refreshTokens.findByUserId(user.getId()).get(0).getUserAgent()).hasSize(255);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGeneric401() throws Exception {
        User user = createUser(Role.USER);

        String wrongPassword = login(user.getEmail(), "no-es-la-contraseña-correcta")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login(uniqueEmail("nadie"), "no-es-la-contraseña-correcta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(auditActions(user.getId())).contains("LOGIN_FAIL");
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLogins()).isEqualTo(1);
    }

    @Test
    void disabledUserGetsTheGeneric401EvenWithTheRightPassword() throws Exception {
        User user = createUser(Role.USER);
        user.setEnabled(false);
        users.save(user);

        login(user.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
    }

    @Test
    void fiveFailuresLockTheAccountFor15MinutesEvenForTheRightPassword() throws Exception {
        User user = createUser(Role.USER);
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "contraseña-equivocada-" + i).andExpect(status().isUnauthorized());
        }
        User locked = users.findById(user.getId()).orElseThrow();
        assertThat(locked.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));

        // Bloqueada: ni la contraseña correcta entra, y el mensaje es el mismo.
        login(user.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
        clock.advance(Duration.ofMinutes(14));
        login(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized());

        // Pasados los 15 minutos vuelve a entrar y se reinicia el contador.
        clock.advance(Duration.ofMinutes(2));
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
        User unlocked = users.findById(user.getId()).orElseThrow();
        assertThat(unlocked.getFailedLogins()).isZero();
        assertThat(unlocked.getLockedUntil()).isNull();
    }

    @Test
    void concurrentFailuresAreAllCountedAndDoNotExhaustTheConnectionPool() throws Exception {
        User user = createUser(Role.USER);
        int attempts = 12; // más hilos que conexiones en el pool (5)
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return login(user.getEmail(), "contraseña-equivocada").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(401);
            }
        } finally {
            executor.shutdownNow();
        }

        // Ningún fallo se pierde por la concurrencia: la cuenta acaba bloqueada.
        assertThat(users.findById(user.getId()).orElseThrow().getLockedUntil()).isNotNull();
        login(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        User user = createUser(Role.USER);
        for (int i = 0; i < 4; i++) {
            login(user.getEmail(), "contraseña-equivocada").andExpect(status().isUnauthorized());
        }
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(user.getEmail(), "contraseña-equivocada").andExpect(status().isUnauthorized());
        }

        User current = users.findById(user.getId()).orElseThrow();
        assertThat(current.getFailedLogins()).isEqualTo(4);
        assertThat(current.getLockedUntil()).isNull();
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void invalidLoginBodyIs400() throws Exception {
        mvc.perform(authPost("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ------------------------------------------------------------------ refresh

    @Test
    void refreshRotatesTheTokenWithinTheSameFamily() throws Exception {
        User user = createUser(Role.USER);
        String first = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());

        MvcResult rotated = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andReturn();
        String second = refreshCookie(rotated);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(setCookieHeader(rotated)).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(rotated))))
                .andExpect(status().isOk());

        RefreshToken old = refreshTokens.findByTokenHash(RefreshTokens.hash(first)).orElseThrow();
        RefreshToken current = refreshTokens.findByTokenHash(RefreshTokens.hash(second)).orElseThrow();
        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(old.getReplacedBy()).isEqualTo(current.getId());
        assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(current.getRevokedAt()).isNull();

        // El nuevo sigue rotando.
        String third = refreshCookie(refresh(second).andExpect(status().isOk()).andReturn());
        assertThat(third).isNotEqualTo(second);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        User user = createUser(Role.USER);
        String first = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        // El viejo ya no vale: es una reutilización (posible robo).
        MvcResult reuse = refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La sesión ha caducado o no es válida"))
                .andReturn();
        assertThat(setCookieHeader(reuse)).contains("refresh_token=;", "Max-Age=0", "Path=/api/auth");

        // ...y tampoco el nuevo: se revocó toda la familia.
        refresh(second).andExpect(status().isUnauthorized());

        UUID family = refreshTokens.findByTokenHash(RefreshTokens.hash(first)).orElseThrow().getFamilyId();
        assertThat(refreshTokens.findByFamilyId(family)).hasSize(2).allMatch(t -> t.getRevokedAt() != null);
        assertThat(auditActions(user.getId())).contains("TOKEN_REUSE");
    }

    /**
     * Dos /refresh simultáneos con la misma cookie (p. ej. dos pestañas) no pueden rotarla los dos:
     * el segundo cuenta como reutilización. El frontend debe serializar sus llamadas a /refresh.
     */
    @Test
    void concurrentRefreshesWithTheSameTokenOnlyRotateOnce() throws Exception {
        User user = createUser(Role.USER);
        String token = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return refresh(token).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }
        assertThat(refreshTokens.findByUserId(user.getId())).hasSize(2).allMatch(t -> t.getRevokedAt() != null);
    }

    @Test
    void reuseOnlyRevokesTheAffectedFamily() throws Exception {
        User user = createUser(Role.USER);
        String laptop = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        String phone = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        refresh(laptop).andExpect(status().isOk());
        refresh(laptop).andExpect(status().isUnauthorized());

        refresh(phone).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieOrWithAnUnknownTokenIs401AndClearsTheCookie() throws Exception {
        MvcResult missing = refresh(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La sesión ha caducado o no es válida"))
                .andReturn();
        assertThat(setCookieHeader(missing)).contains("Max-Age=0");

        MvcResult unknown = refresh("A".repeat(43)).andExpect(status().isUnauthorized()).andReturn();
        assertThat(setCookieHeader(unknown)).contains("Max-Age=0");

        refresh("no-tiene-el-formato").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIs401() throws Exception {
        User user = createUser(Role.USER);
        String token = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());

        clock.advance(Duration.ofDays(7).plusMinutes(1));

        MvcResult result = refresh(token).andExpect(status().isUnauthorized()).andReturn();
        assertThat(setCookieHeader(result)).contains("Max-Age=0");
    }

    @Test
    void refreshForADisabledUserIs401() throws Exception {
        User user = createUser(Role.USER);
        String token = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        user = users.findById(user.getId()).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessTokenIs401OnTheApiButDoesNotBlockRefresh() throws Exception {
        User user = createUser(Role.USER);
        String refreshToken = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject(user.getId().toString())
                        .claim("role", "USER")
                        .issuedAt(past)
                        .expiresAt(past.plus(Duration.ofMinutes(15)))
                        .id(UUID.randomUUID().toString())
                        .build())).getTokenValue();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());

        // En /api/auth/* se ignora Authorization: el token caducado no estorba al refresh.
        mvc.perform(authPost("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expired))
                        .cookie(new Cookie(RefreshCookies.NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        User user = createUser(Role.USER);
        String token = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());

        MvcResult result = mvc.perform(authPost("/api/auth/logout").cookie(new Cookie(RefreshCookies.NAME, token)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(setCookieHeader(result)).contains("refresh_token=;", "Max-Age=0", "Path=/api/auth", "HttpOnly");

        refresh(token).andExpect(status().isUnauthorized());
        assertThat(refreshTokens.findByTokenHash(RefreshTokens.hash(token)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(auditActions(user.getId())).contains("LOGOUT");
    }

    @Test
    void logoutWithoutCookieIsStill204() throws Exception {
        MvcResult result = mvc.perform(authPost("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(setCookieHeader(result)).contains("Max-Age=0");
    }

    // ------------------------------------------------------------------ Origin

    @Test
    void authEndpointsRejectMissingOrForeignOrigin() throws Exception {
        User user = createUser(Role.USER);
        String credentials = body(Map.of("email", user.getEmail(), "password", PASSWORD));

        // Sin Origin ni Referer.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Origen no permitido"));

        // Origin ajeno, aunque las credenciales sean buenas.
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "null")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());
        // Un Origin ajeno no se compensa con un Referer válido.
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.REFERER, ORIGIN + "/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());

        for (String path : List.of("/api/auth/refresh", "/api/auth/logout", "/api/auth/register")) {
            mvc.perform(post(path).header(HttpHeaders.ORIGIN, "https://evil.example"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path)).andExpect(status().isForbidden());
        }

        // Sin Origin, vale el origen del Referer.
        mvc.perform(post("/api/auth/login").header(HttpHeaders.REFERER, ORIGIN + "/login?next=%2F")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header(HttpHeaders.REFERER, "https://evil.example/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ registro

    @Test
    void registrationIsClosedByDefault() throws Exception {
        String email = uniqueEmail("nuevo");
        mvc.perform(authPost("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Nuevo", "email", email, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("El registro está cerrado"));
        // Cerrado incluso con un cuerpo inválido o sin cuerpo.
        mvc.perform(authPost("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(authPost("/api/auth/register")).andExpect(status().isForbidden());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    // ------------------------------------------------------------------ limpieza

    @Test
    void nightlyCleanupDeletesOnlyExpiredRefreshTokens() throws Exception {
        User user = createUser(Role.USER);
        String oldToken = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        clock.advance(Duration.ofDays(5));
        String recentToken = refreshCookie(login(user.getEmail(), PASSWORD).andReturn());
        clock.advance(Duration.ofDays(3));

        cleanup.deleteExpired();

        assertThat(refreshTokens.findByTokenHash(RefreshTokens.hash(oldToken))).isEmpty();
        assertThat(refreshTokens.findByTokenHash(RefreshTokens.hash(recentToken))).isPresent();
    }
}
