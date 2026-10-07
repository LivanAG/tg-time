package com.controlhorario.user;

import jakarta.validation.Valid;

import com.controlhorario.auth.RefreshCookies;
import com.controlhorario.common.security.CurrentUser;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario autenticado. El usuario sale siempre del token. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;
    private final UserMapper mapper;
    private final CurrentUser currentUser;
    private final RefreshCookies cookies;

    public MeController(UserService userService, UserMapper mapper, CurrentUser currentUser, RefreshCookies cookies) {
        this.userService = userService;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.cookies = cookies;
    }

    @GetMapping
    public UserDto get() {
        return mapper.toDto(userService.get(currentUser.id()));
    }

    @PutMapping
    public UserDto update(@Valid @RequestBody UpdateProfileRequest request) {
        return mapper.toDto(userService.updateProfile(currentUser.id(), request));
    }

    /**
     * 204. Revoca todas las sesiones del usuario y borra la cookie de refresco de este navegador:
     * el frontend debe volver a pedir login.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.id(), request);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
}
