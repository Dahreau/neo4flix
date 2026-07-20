package com.neo4flix.user_service.web;

import com.neo4flix.user_service.dto.AuthResponse;
import com.neo4flix.user_service.dto.TwoFactorCodeRequest;
import com.neo4flix.user_service.dto.TwoFactorLoginRequest;
import com.neo4flix.user_service.dto.TwoFactorSetupResponse;
import com.neo4flix.user_service.service.TwoFactorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/setup")
    public TwoFactorSetupResponse setup(Principal principal) {
        return twoFactorService.setup(principal.getName());
    }

    @PostMapping("/enable")
    public ResponseEntity<Void> enable(Principal principal, @RequestBody TwoFactorCodeRequest request) {
        twoFactorService.enable(principal.getName(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(Principal principal, @RequestBody TwoFactorCodeRequest request) {
        twoFactorService.disable(principal.getName(), request.code());
        return ResponseEntity.noContent().build();
    }

    // Public: called after a login response comes back with requiresTwoFactor=true.
    @PostMapping("/verify-login")
    public AuthResponse verifyLogin(@RequestBody TwoFactorLoginRequest request) {
        return twoFactorService.verifyLogin(request.username(), request.code());
    }
}
