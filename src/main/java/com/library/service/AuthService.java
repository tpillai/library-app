package com.library.service;

import com.library.security.JwtService;
import com.library.web.dto.LoginRequest;
import com.library.web.dto.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    // Constructor injection - no @Autowired field injection.
    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        // Delegates to the DaoAuthenticationProvider, which loads the Borrower via
        // UserDetailsService and checks the password with the BCryptPasswordEncoder bean.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String token = jwtService.generateToken(request.email(), roles);
        return new LoginResponse(token, jwtService.getTtlSeconds());
    }
}
