package com.controlhorario.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlhorario.user.AdminBootstrap;
import com.controlhorario.user.Role;
import com.controlhorario.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Registro abierto (APP_REGISTRATION_OPEN=true) y creación del primer administrador al arrancar. */
@TestPropertySource(properties = {
        "app.registration-open=true",
        "app.admin.email=  Jefa@Example.com ",
        "app.admin.password=frase-inicial-del-admin-2026",
        "app.admin.name=Jefa Inicial"})
class RegistrationIT extends AuthTestSupport {

    @Autowired
    AdminBootstrap bootstrap;

    // ------------------------------------------------------------------ primer usuario

    @Test
    void initialAdminIsCreatedAtStartup() throws Exception {
        User admin = users.findByEmailIgnoreCase("jefa@example.com").orElseThrow();
        assertThat(admin.getEmail()).isEqualTo("jefa@example.com");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getName()).isEqualTo("Jefa Inicial");
        assertThat(admin.getCompany()).isNull();
        assertThat(admin.getTimezone()).isEqualTo("Europe/Madrid");
        assertThat(admin.getPasswordHash()).startsWith("$argon2id$").doesNotContain("frase-inicial");

        login("jefa@example.com", "frase-inicial-del-admin-2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));

        // Con usuarios ya existentes no vuelve a crear nada.
        assertThat(bootstrap.createInitialAdmin()).isEmpty();
        assertThat(users.findAll()).filteredOn(u -> u.getEmail().equals("jefa@example.com")).hasSize(1);
        assertThat(auditLog.findByActionOrderByAtDesc("USER_CREATE"))
                .anyMatch(a -> admin.getId().toString().equals(a.getEntityId()));
    }

    // ------------------------------------------------------------------ registro abierto

    @Test
    void registerCreatesAUserThatCanLogIn() throws Exception {
        String email = uniqueEmail("Nueva");
        Map<String, Object> request = registration(email, PASSWORD);
        request.put("company", "  ");
        request.put("timezone", null);
        request.put("role", "ADMIN"); // no se puede elegir el rol: se ignora

        MvcResult result = register(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.name").value("Livan Aranda"))
                .andExpect(jsonPath("$.company").isEmpty())
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        UUID id = UUID.fromString(read(result).get("id").asText());
        assertThat(auditActions(id)).contains("REGISTER");

        login(email, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void registerKeepsCompanyAndTimezone() throws Exception {
        Map<String, Object> request = registration(uniqueEmail("con-empresa"), PASSWORD);
        request.put("company", "IZERTIS");
        request.put("timezone", "Atlantic/Canary");

        register(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("IZERTIS"))
                .andExpect(jsonPath("$.timezone").value("Atlantic/Canary"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = uniqueEmail("repetida");
        register(registration(email, PASSWORD)).andExpect(status().isCreated());

        register(registration(email.toUpperCase(), PASSWORD)).andExpect(status().isConflict());
    }

    @Test
    void registerAppliesThePasswordPolicy() throws Exception {
        String email = uniqueEmail("politica");
        String localPart = email.substring(0, email.indexOf('@'));
        Map<String, String> cases = Map.of(
                "corta-1234", "La contraseña debe tener entre 12 y 128 caracteres",
                "Contraseña123", "La contraseña es demasiado común; elige otra",
                "1q2w3e4r5t6y", "La contraseña es demasiado común; elige otra",
                email, "La contraseña no puede ser igual al email",
                localPart.toUpperCase(), "La contraseña no puede ser igual al email");
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            register(registration(email, entry.getKey()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"))
                    .andExpect(jsonPath("$.errors[0].message").value(entry.getValue()));
        }
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void registerValidatesTheBody() throws Exception {
        Map<String, Object> badTimezone = registration(uniqueEmail("zona"), PASSWORD);
        badTimezone.put("timezone", "Madrid");
        register(badTimezone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timezone"))
                .andExpect(jsonPath("$.errors[0].message").value("Zona horaria no válida"));

        MvcResult missing = register(new HashMap<>(Map.of("email", "no-es-un-email")))
                .andExpect(status().isBadRequest())
                .andReturn();
        List<String> fields = read(missing).get("errors").findValuesAsText("field");
        assertThat(fields).contains("name", "email", "password");
    }

    private Map<String, Object> registration(String email, String password) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Livan Aranda");
        request.put("email", email);
        request.put("password", password);
        return request;
    }

    private ResultActions register(Map<String, Object> request) throws Exception {
        return mvc.perform(authPost("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }
}
