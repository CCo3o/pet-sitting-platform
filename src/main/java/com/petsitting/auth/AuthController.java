package com.petsitting.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    public record RegisterRequest(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(min=8, max=64) String password,
        @NotBlank @Size(max=40) String displayName,
        @NotNull @Pattern(regexp="OWNER|SITTER") String role) {}
    public record LoginRequest(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(min=8, max=64) String password) {}
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterRequest body) {
        return service.register(body.username(), body.password(), body.displayName(), body.role());
    }
    @PostMapping("/auth/login")
    public AuthService.AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return service.login(body.username(), body.password());
    }
    @GetMapping("/auth/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) { return service.user(AuthService.actor(jwt)); }
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) { service.logout(jwt); }
    @GetMapping("/admin/users")
    public List<UserView> users() { return service.users(); }
    public record AccountStatus(@NotNull Boolean enabled){}
    @PatchMapping("/admin/users/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accountStatus(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody AccountStatus body){service.accountStatus(AuthService.actor(jwt),id,body.enabled());}
}
