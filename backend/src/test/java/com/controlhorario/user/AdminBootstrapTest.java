package com.controlhorario.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.config.AppProperties;
import com.controlhorario.common.security.PasswordPolicy;
import com.controlhorario.common.web.ConflictException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PasswordPolicy policy = new PasswordPolicy();

    private AdminBootstrap bootstrap(String email, String password, String name) {
        AppProperties properties = new AppProperties("x".repeat(48), Duration.ofMinutes(15), Duration.ofDays(7), true,
                List.of("https://horas.example.com"), false, new AppProperties.Admin(email, password, name));
        return new AdminBootstrap(properties, users, userService, policy, audit);
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void createsTheFirstAdminWhenThereAreNoUsers() {
        when(users.count()).thenReturn(0L);
        User created = user("jefa@example.com");
        when(userService.create(any())).thenReturn(created);

        Optional<User> result = bootstrap("jefa@example.com", "controlhorario-dev", "Jefa").createInitialAdmin();

        assertThat(result).containsSame(created);
        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userService).create(captor.capture());
        NewUser data = captor.getValue();
        assertThat(data.role()).isEqualTo(Role.ADMIN);
        assertThat(data.name()).isEqualTo("Jefa");
        assertThat(data.email()).isEqualTo("jefa@example.com");
        assertThat(data.company()).isNull();
        assertThat(data.timezone()).isEqualTo("Europe/Madrid");
        assertThat(data.toString()).doesNotContain("controlhorario-dev");
        verify(audit).record(isNull(), eq("USER_CREATE"), eq("user"), eq(created.getId().toString()));
    }

    @Test
    void defaultsTheNameWhenBlank() {
        when(users.count()).thenReturn(0L);
        when(userService.create(any())).thenReturn(user("jefa@example.com"));

        assertThat(bootstrap("jefa@example.com", "controlhorario-dev", " ").createInitialAdmin()).isPresent();

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Administrador");
    }

    @Test
    void doesNothingWhenThereAreUsers() {
        when(users.count()).thenReturn(1L);

        assertThat(bootstrap("jefa@example.com", "controlhorario-dev", "Jefa").createInitialAdmin()).isEmpty();
        verify(userService, never()).create(any());
    }

    @Test
    void doesNothingWithoutEmailOrPassword() {
        when(users.count()).thenReturn(0L);

        assertThat(bootstrap(null, "controlhorario-dev", "Jefa").createInitialAdmin()).isEmpty();
        assertThat(bootstrap("jefa@example.com", null, "Jefa").createInitialAdmin()).isEmpty();
        assertThat(bootstrap(" ", "", "Jefa").createInitialAdmin()).isEmpty();
        verify(userService, never()).create(any());
    }

    @Test
    void doesNotCreateTheAdminWhenThePasswordBreaksThePolicy() {
        when(users.count()).thenReturn(0L);

        assertThat(bootstrap("jefa@example.com", "cambia-esto", "Jefa").createInitialAdmin()).isEmpty();
        assertThat(bootstrap("jefa@example.com", "password1234", "Jefa").createInitialAdmin()).isEmpty();
        assertThat(bootstrap("jefa@example.com", "JEFA@example.com", "Jefa").createInitialAdmin()).isEmpty();
        verify(userService, never()).create(any());
        verify(audit, never()).record(any(), anyString(), anyString(), anyString());
    }

    @Test
    void survivesAFailedCreation() {
        when(users.count()).thenReturn(0L);
        when(userService.create(any())).thenThrow(new ConflictException("Ya existe un usuario con ese email"));

        assertThat(bootstrap("jefa@example.com", "controlhorario-dev", "Jefa").createInitialAdmin()).isEmpty();
    }
}
