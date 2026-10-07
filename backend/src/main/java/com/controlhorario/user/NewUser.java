package com.controlhorario.user;

/**
 * Datos para crear un usuario (registro, alta por invitación o primer administrador).
 * {@code company} y {@code timezone} son opcionales; la zona por defecto es Europe/Madrid.
 */
public record NewUser(String name, String email, String password, String company, String timezone, Role role) {

    /** Nunca incluye la contraseña (por si acaba en un log). */
    @Override
    public String toString() {
        return "NewUser[email=" + email + ", role=" + role + "]";
    }
}
