package com.controlhorario.user;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.controlhorario.auth.RefreshTokenRepository;
import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.security.PasswordPolicy;
import com.controlhorario.common.web.ConflictException;
import com.controlhorario.common.web.NotFoundException;
import com.controlhorario.common.web.ValidationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Alta de usuarios, perfil y cambio de contraseña. */
@Service
public class UserService {

    static final int MAX_EMAIL_LENGTH = 254;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, AuditService audit, Clock clock, TransactionTemplate transaction) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.clock = clock;
        this.transaction = transaction;
    }

    /** Email tal y como se guarda y se busca: sin espacios y en minúsculas. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Crea un usuario aplicando la política de contraseñas. 400 si algún dato no es válido, 409 si
     * el email ya existe (sin distinguir mayúsculas). La auditoría la registra quien llama
     * (REGISTER, USER_CREATE), porque cada vía de alta tiene su propia acción. Sin transacción
     * envolvente: el hash Argon2 no retiene una conexión y el índice único resuelve las carreras.
     */
    public User create(NewUser data) {
        String email = normalizeEmail(data.email());
        if (!looksLikeEmail(email)) {
            throw new ValidationException("email", "El email no es válido");
        }
        String name = data.name() == null ? "" : data.name().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new ValidationException("name", "El nombre es obligatorio y admite como máximo 100 caracteres");
        }
        String timezone = blankToNull(data.timezone());
        if (timezone == null) {
            timezone = Timezones.DEFAULT;
        } else if (!Timezones.isValid(timezone)) {
            throw new ValidationException("timezone", "Zona horaria no válida");
        }
        passwordPolicy.check("password", data.password(), email);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Ya existe un usuario con ese email");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setCompany(blankToNull(data.company()));
        user.setTimezone(timezone);
        user.setRole(data.role() == null ? Role.USER : data.role());
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(data.password()));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe un usuario con ese email");
        }
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return users.findAll(Sort.by("createdAt", "email"));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = get(userId);
        user.setName(request.name().strip());
        user.setCompany(blankToNull(request.company()));
        user.setTimezone(request.timezone());
        return user;
    }

    /**
     * Verifica la contraseña actual, aplica la política a la nueva y revoca todas las sesiones
     * (refresh tokens) del usuario: hay que volver a iniciar sesión en todos los dispositivos.
     * Los hashes Argon2 y la auditoría quedan fuera de la transacción, que solo guarda el cambio.
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = get(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("currentPassword", "La contraseña actual no es correcta");
        }
        passwordPolicy.check("newPassword", request.newPassword(), user.getEmail());
        String checkedHash = user.getPasswordHash();
        String newHash = passwordEncoder.encode(request.newPassword());
        transaction.executeWithoutResult(status -> {
            User locked = users.findByIdForUpdate(userId)
                    .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
            if (!locked.getPasswordHash().equals(checkedHash)) {
                throw new ConflictException("La contraseña ha cambiado mientras tanto. Vuelve a intentarlo.");
            }
            locked.setPasswordHash(newHash);
            refreshTokens.revokeAllForUser(userId, clock.instant());
        });
        audit.record(userId, "PASSWORD_CHANGE", "user", userId.toString());
    }

    private static boolean looksLikeEmail(String email) {
        int at = email.indexOf('@');
        return email.length() <= MAX_EMAIL_LENGTH && at > 0 && at == email.lastIndexOf('@')
                && at < email.length() - 1 && email.chars().noneMatch(Character::isWhitespace);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
