package com.library.web;

import com.library.service.AuthService;
import com.library.web.dto.LoginRequest;
import com.library.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    // Constructor injection - no @Autowired field injection.
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Open endpoint - permitAll() in SecurityConfig.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
