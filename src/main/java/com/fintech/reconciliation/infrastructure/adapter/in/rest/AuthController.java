package com.fintech.reconciliation.infrastructure.adapter.in.rest;

import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.AuthRequestDto;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.AuthResponseDto;
import com.fintech.reconciliation.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Issues JWT tokens for development/testing.
 * In production, replace the in-memory user map with a UserDetailsService backed by a DB.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Obtain a Bearer JWT token for API access")
public class AuthController {

    private static final Map<String, String> DEV_USERS = Map.of(
        "admin", "admin123",
        "reconciler", "reconciler2024"
    );

    private final JwtTokenProvider tokenProvider;

    @PostMapping("/token")
    @Operation(summary = "Request a JWT", description = "Validates credentials and returns a signed Bearer token.")
    public ResponseEntity<AuthResponseDto> getToken(@RequestBody @Valid AuthRequestDto request) {
        String stored = DEV_USERS.get(request.username());
        if (stored == null || !stored.equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponseDto(null, null, "Invalid credentials"));
        }
        String token = tokenProvider.generateToken(request.username());
        return ResponseEntity.ok(new AuthResponseDto(token, "Bearer", null));
    }
}
