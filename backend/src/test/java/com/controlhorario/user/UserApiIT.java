package com.controlhorario.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.auth.AuthTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** /api/me y /api/admin/users, con tokens reales obtenidos por login. */
class UserApiIT extends AuthTestSupport {

    // ------------------------------------------------------------------ /api/me

    @Test
    void getMeReturnsTheUserOfTheToken() throws Exception {
        User user = createUser(Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.name").value("Persona de prueba"))
                .andExpect(jsonPath("$.company").value("IZERTIS"))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLogins").doesNotExist());
    }

    @Test
    void getMeWithoutTokenIs401AndForAnUnknownUserIs404() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("role", "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfile() throws Exception {
        User user = createUser(Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());

        Map<String, Object> changes = new HashMap<>();
        changes.put("name", "  Livan Aranda  ");
        changes.put("company", null);
        changes.put("timezone", "America/Bogota");
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(changes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Livan Aranda"))
                .andExpect(jsonPath("$.company").isEmpty())
                .andExpect(jsonPath("$.timezone").value("America/Bogota"))
                .andExpect(jsonPath("$.email").value(user.getEmail()));

        User stored = users.findById(user.getId()).orElseThrow();
        assertThat(stored.getTimezone()).isEqualTo("America/Bogota");
        assertThat(stored.getCompany()).isNull();
    }

    @Test
    void updateProfileValidatesTimezoneAndName() throws Exception {
        User user = createUser(Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());

        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Livan", "timezone", "Europe/Atlantis"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timezone"))
                .andExpect(jsonPath("$.errors[0].message").value("Zona horaria no válida"));
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", " ", "timezone", "Europe/Madrid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    // ------------------------------------------------------------------ /api/me/password

    @Test
    void changingThePasswordRevokesEverySession() throws Exception {
        User user = createUser(Role.USER);
        MvcResult laptop = login(user.getEmail(), PASSWORD).andReturn();
        MvcResult phone = login(user.getEmail(), PASSWORD).andReturn();
        String newPassword = "otra-frase-mucho-mas-larga-2027";

        MvcResult result = changePassword(accessToken(laptop), PASSWORD, newPassword)
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(setCookieHeader(result)).contains("Max-Age=0", "Path=/api/auth");

        // Ninguna sesión sobrevive: hay que volver a iniciar sesión en todos los dispositivos.
        refresh(refreshCookie(laptop)).andExpect(status().isUnauthorized());
        refresh(refreshCookie(phone)).andExpect(status().isUnauthorized());
        assertThat(refreshTokens.findByUserId(user.getId())).allMatch(t -> t.getRevokedAt() != null);

        login(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
        login(user.getEmail(), newPassword).andExpect(status().isOk());
        assertThat(auditActions(user.getId())).contains("PASSWORD_CHANGE");
    }

    @Test
    void changingThePasswordRequiresTheCurrentOne() throws Exception {
        User user = createUser(Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());

        changePassword(token, "no-es-la-actual-de-verdad", "otra-frase-mucho-mas-larga-2027")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("La contraseña actual no es correcta"));
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustFollowThePolicy() throws Exception {
        User user = createUser(uniqueEmail("politica"), PASSWORD, Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());
        String localPart = user.getEmail().substring(0, user.getEmail().indexOf('@'));

        Map<String, String> cases = Map.of(
                "corta-11ch", "La contraseña debe tener entre 12 y 128 caracteres",
                "x".repeat(129), "La contraseña debe tener entre 12 y 128 caracteres",
                "PasswordPassword", "La contraseña es demasiado común; elige otra",
                "qwerty123456", "La contraseña es demasiado común; elige otra",
                user.getEmail().toUpperCase(), "La contraseña no puede ser igual al email",
                localPart, "La contraseña no puede ser igual al email");
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            changePassword(token, PASSWORD, entry.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].message").value(entry.getValue()));
        }
        // Ninguno de los intentos cambió nada.
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ /api/admin/users

    @Test
    void adminEndpointsAreForbiddenForUsers() throws Exception {
        User user = createUser(Role.USER);
        String token = accessToken(login(user.getEmail(), PASSWORD).andReturn());

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(newUser(uniqueEmail("invitado"), "ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesAndListsUsers() throws Exception {
        User admin = createUser(Role.ADMIN);
        String token = accessToken(login(admin.getEmail(), PASSWORD).andReturn());
        String email = uniqueEmail("Invitada");

        MvcResult created = mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(newUser(email, "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.name").value("Invitada"))
                .andExpect(jsonPath("$.company").value("IZERTIS"))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        UUID createdId = UUID.fromString(read(created).get("id").asText());

        // Sin rol: USER.
        Map<String, Object> plain = newUser(uniqueEmail("invitado"), null);
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(plain)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        // Email repetido (sin distinguir mayúsculas): 409.
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(newUser(email.toUpperCase(), "USER"))))
                .andExpect(status().isConflict());

        // Política de contraseñas.
        Map<String, Object> weak = newUser(uniqueEmail("debil"), "USER");
        weak.put("password", "123456789012");
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body(weak)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        MvcResult list = mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> emails = read(list).findValuesAsText("email");
        assertThat(emails).contains(admin.getEmail(), email.toLowerCase());
        assertThat(read(list).findValues("passwordHash")).isEmpty();

        assertThat(auditLog.findByActionOrderByAtDesc("USER_CREATE"))
                .anyMatch(a -> admin.getId().equals(a.getUserId()) && createdId.toString().equals(a.getEntityId()));

        // La persona invitada puede entrar con la contraseña que le dieron.
        login(email, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    private Map<String, Object> newUser(String email, String role) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Invitada");
        request.put("email", email);
        request.put("password", PASSWORD);
        request.put("company", "IZERTIS");
        request.put("timezone", null);
        request.put("role", role);
        return request;
    }

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("currentPassword", current, "newPassword", next))));
    }
}
