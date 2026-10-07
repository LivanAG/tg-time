package com.controlhorario.user;

import java.util.List;

import jakarta.validation.Valid;

import com.controlhorario.common.audit.AuditService;
import com.controlhorario.common.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administración de usuarios (rol ADMIN): listado y alta "por invitación". */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final UserMapper mapper;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public AdminUserController(UserService userService, UserMapper mapper, CurrentUser currentUser,
            AuditService audit) {
        this.userService = userService;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping
    public List<UserDto> list() {
        return mapper.toDtos(userService.findAll());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(new NewUser(request.name(), request.email(), request.password(),
                request.company(), request.timezone(), request.role()));
        audit.record(currentUser.id(), "USER_CREATE", "user", user.getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(user));
    }
}
