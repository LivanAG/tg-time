package com.controlhorario.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.config.AppProperties;
import com.controlhorario.user.NewUser;
import com.controlhorario.user.Role;
import com.controlhorario.user.User;
import com.controlhorario.user.UserDto;
import com.controlhorario.user.UserMapper;
import com.controlhorario.user.UserRepository;
import com.controlhorario.user.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sesiones: login con bloqueo tras 5 fallos, refresh token rotativo con detección de reutilización
 * (revoca toda la familia), logout y registro abierto opcional.
 *
 * <p>Los hashes Argon2 y la auditoría (que abre su propia transacción) se hacen fuera de las
 * transacciones; estas son cortas y solo bloquean la fila que modifican. Así una ráfaga de logins
 * no retiene conexiones del pool mientras calcula hashes ni necesita dos conexiones a la vez.
 */
@Service
public class AuthService {

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    static final String INVALID_CREDENTIALS = "Email o contraseña incorrectos";
    static final String INVALID_SESSION = "La sesión ha caducado o no es válida";
    static final String REGISTRATION_CLOSED = "El registro está cerrado";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final UserService userService;
    private final UserMapper userMapper;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    /** Hash con el que se compara cuando el email no existe: mismo coste, sin oráculo de tiempo. */
    private final String dummyHash;

    public AuthService(UserRepository users, UserService userService, UserMapper userMapper,
            RefreshTokenRepository refreshTokens, AccessTokenService accessTokens, PasswordEncoder passwordEncoder,
            AuditService audit, AppProperties properties, Clock clock, TransactionTemplate transaction) {
        this.users = users;
        this.userService = userService;
        this.userMapper = userMapper;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.transaction = transaction;
        this.dummyHash = passwordEncoder.encode(RefreshTokens.generate(random));
    }

    /** Resultado de abrir o renovar sesión: cuerpo de la respuesta y refresh token para la cookie. */
    public record Session(AuthResponse response, String refreshToken) {

        /** Nunca incluye el refresh token (por si acaba en un log). */
        @Override
        public String toString() {
            return "Session[" + response + "]";
        }
    }

    /**
     * Mismo 401 genérico si el email no existe, la contraseña no coincide, la cuenta está
     * deshabilitada o bloqueada. Siempre se calcula un hash Argon2, exista o no el usuario.
     */
    public Session login(String email, String password, ClientInfo client) {
        Optional<User> found = users.findByEmailIgnoreCase(UserService.normalizeEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(null, "LOGIN_FAIL", "user", null);
            throw invalidCredentials();
        }
        UUID userId = found.get().getId();
        String checkedHash = found.get().getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, checkedHash);

        Session session = transaction.execute(status -> completeLogin(userId, checkedHash, passwordMatches, client));
        if (session == null) {
            audit.record(userId, "LOGIN_FAIL", "user", userId.toString());
            throw invalidCredentials();
        }
        audit.record(userId, "LOGIN_OK", "user", userId.toString());
        return session;
    }

    /**
     * Rota el refresh token: el actual queda revocado y apunta al nuevo (misma familia). Si llega
     * uno ya revocado, alguien lo está reutilizando: se revoca toda la familia (detección de robo).
     * Desconocido, caducado o de un usuario deshabilitado: 401 y se borra la cookie.
     */
    public Session refresh(String rawToken, ClientInfo client) {
        if (!RefreshTokens.isWellFormed(rawToken)) {
            throw invalidSession();
        }
        Rotation rotation = transaction.execute(status -> rotate(RefreshTokens.hash(rawToken), client));
        if (rotation.reused() != null) {
            RefreshToken reused = rotation.reused();
            audit.record(reused.getUserId(), "TOKEN_REUSE", "refresh_token", reused.getFamilyId().toString());
            log.warn("Reutilización de un refresh token ya revocado: se revoca la familia {} del usuario {}",
                    reused.getFamilyId(), reused.getUserId());
        }
        if (rotation.session() == null) {
            throw invalidSession();
        }
        return rotation.session();
    }

    /** Revoca la sesión de la cookie (si existe y sigue activa). Nunca falla. */
    public void logout(String rawToken) {
        if (!RefreshTokens.isWellFormed(rawToken)) {
            return;
        }
        UUID userId = transaction.execute(status -> revoke(RefreshTokens.hash(rawToken)));
        if (userId != null) {
            audit.record(userId, "LOGOUT", "user", userId.toString());
        }
    }

    /** 403 si el registro está cerrado (APP_REGISTRATION_OPEN=false, por defecto). */
    public void ensureRegistrationOpen() {
        if (!properties.registrationOpen()) {
            throw new AuthException(HttpStatus.FORBIDDEN, REGISTRATION_CLOSED, false);
        }
    }

    /** Alta libre: siempre con rol USER. 409 si el email existe; 400 si no cumple la política. */
    public UserDto register(RegisterRequest request) {
        ensureRegistrationOpen();
        User user = userService.create(new NewUser(request.name(), request.email(), request.password(),
                request.company(), request.timezone(), Role.USER));
        audit.record(user.getId(), "REGISTER", "user", user.getId().toString());
        return userMapper.toDto(user);
    }

    // ------------------------------------------------------------------ dentro de transacción

    /** Con la fila del usuario bloqueada: aplica el intento. Devuelve null si se rechaza. */
    private Session completeLogin(UUID userId, String checkedHash, boolean passwordMatches, ClientInfo client) {
        Instant now = clock.instant();
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || !user.isEnabled() || isLocked(user, now)) {
            return null;
        }
        if (!user.getPasswordHash().equals(checkedHash)) {
            // La contraseña cambió mientras se comprobaba: se rechaza sin contar como fallo.
            return null;
        }
        if (!passwordMatches) {
            registerFailure(user, now);
            return null;
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        return openSession(user, UUID.randomUUID(), client, now).session();
    }

    private record Rotation(Session session, RefreshToken reused) {
    }

    private Rotation rotate(String tokenHash, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(tokenHash).orElse(null);
        if (current == null) {
            return new Rotation(null, null);
        }
        if (current.getRevokedAt() != null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return new Rotation(null, current);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return new Rotation(null, null);
        }
        Optional<User> user = users.findById(current.getUserId()).filter(User::isEnabled);
        if (user.isEmpty()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return new Rotation(null, null);
        }
        Opened next = openSession(user.get(), current.getFamilyId(), client, now);
        current.setRevokedAt(now);
        current.setReplacedBy(next.token().getId());
        return new Rotation(next.session(), null);
    }

    /** Revoca la familia del token si sigue activo; devuelve su usuario o null. */
    private UUID revoke(String tokenHash) {
        Instant now = clock.instant();
        return refreshTokens.findByTokenHashForUpdate(tokenHash)
                .filter(token -> token.getRevokedAt() == null)
                .map(token -> {
                    refreshTokens.revokeFamily(token.getFamilyId(), now);
                    return token.getUserId();
                })
                .orElse(null);
    }

    private boolean isLocked(User user, Instant now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    private void registerFailure(User user, Instant now) {
        int failures = user.getFailedLogins() + 1;
        if (failures >= MAX_FAILED_LOGINS) {
            user.setFailedLogins(0);
            user.setLockedUntil(now.plus(LOCK_DURATION));
            log.warn("Cuenta {} bloqueada {} min tras {} intentos fallidos", user.getId(),
                    LOCK_DURATION.toMinutes(), MAX_FAILED_LOGINS);
        } else {
            user.setFailedLogins(failures);
        }
    }

    private record Opened(Session session, RefreshToken token) {
    }

    private Opened openSession(User user, UUID familyId, ClientInfo client, Instant now) {
        String raw = RefreshTokens.generate(random);
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(RefreshTokens.hash(raw));
        token.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        token.setUserAgent(client.userAgent());
        token.setIp(client.ip());
        RefreshToken saved = refreshTokens.save(token);
        AuthResponse response = new AuthResponse(accessTokens.issue(user, now), accessTokens.expiresInSeconds(),
                userMapper.toDto(user));
        return new Opened(new Session(response, raw), saved);
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, false);
    }

    private AuthException invalidSession() {
        return new AuthException(HttpStatus.UNAUTHORIZED, INVALID_SESSION, true);
    }
}
