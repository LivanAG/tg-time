package com.controlhorario.user;

import java.util.UUID;

/** Usuario tal y como lo ve la API (nunca el hash de la contraseña ni el estado de bloqueo). */
public record UserDto(UUID id, String email, String name, String company, String timezone, Role role) {
}
