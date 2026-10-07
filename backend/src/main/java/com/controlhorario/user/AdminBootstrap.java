package com.controlhorario.user;

import java.util.Optional;

import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.config.AppProperties;
import com.controlhorario.common.security.PasswordPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Primer usuario: al arrancar, si no hay ningún usuario y están definidos APP_ADMIN_EMAIL y
 * APP_ADMIN_PASSWORD, crea un ADMIN (empresa vacía, zona Europe/Madrid). Si la contraseña no
 * cumple la política no lo crea y lo deja claro en el log. La contraseña nunca se registra.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String DEFAULT_NAME = "Administrador";

    private final AppProperties properties;
    private final UserRepository users;
    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;

    public AdminBootstrap(AppProperties properties, UserRepository users, UserService userService,
            PasswordPolicy passwordPolicy, AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        createInitialAdmin();
    }

    /** Devuelve el administrador creado, o vacío si no hacía falta o no se pudo crear. */
    public Optional<User> createInitialAdmin() {
        if (users.count() > 0) {
            return Optional.empty();
        }
        AppProperties.Admin admin = properties.admin();
        String email = admin == null ? null : admin.email();
        String password = admin == null ? null : admin.password();
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            log.info("No hay usuarios. Define APP_ADMIN_EMAIL y APP_ADMIN_PASSWORD para crear el administrador inicial.");
            return Optional.empty();
        }
        Optional<String> violation = passwordPolicy.violation(password, email);
        if (violation.isPresent()) {
            log.error("No se crea el administrador inicial {}: APP_ADMIN_PASSWORD no cumple la política de "
                    + "contraseñas ({}). Cámbiala en .env y reinicia el backend.", email.strip(), violation.get());
            return Optional.empty();
        }
        String name = admin.name() == null || admin.name().isBlank() ? DEFAULT_NAME : admin.name();
        try {
            User created = userService.create(new NewUser(name, email, password, null, Timezones.DEFAULT, Role.ADMIN));
            audit.record(null, "USER_CREATE", "user", created.getId().toString());
            log.info("Administrador inicial creado: {}", created.getEmail());
            return Optional.of(created);
        } catch (RuntimeException e) {
            log.error("No se pudo crear el administrador inicial {}: {}", email.strip(), e.getMessage());
            return Optional.empty();
        }
    }
}
