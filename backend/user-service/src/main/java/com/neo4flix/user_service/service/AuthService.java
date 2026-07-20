package com.neo4flix.user_service.service;

import com.neo4flix.user_service.domain.User;
import com.neo4flix.user_service.dto.AuthResponse;
import com.neo4flix.user_service.dto.LoginRequest;
import com.neo4flix.user_service.dto.LoginResponse;
import com.neo4flix.user_service.dto.RegisterRequest;
import com.neo4flix.user_service.exception.DuplicateUserException;
import com.neo4flix.user_service.exception.InvalidCredentialsException;
import com.neo4flix.user_service.exception.WeakPasswordException;
import com.neo4flix.user_service.repository.UserRepository;
import com.neo4flix.user_service.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        validatePasswordStrength(request.password());

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Ce nom d'utilisateur est deja pris : " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Cet email est deja utilise : " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .createdAt(Instant.now())
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new AuthResponse(token, user.getUserId(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.isTwoFactorEnabled()) {
            // Password checked out, but the client still needs to hit /api/auth/2fa/verify-login
            // with a TOTP code before getting a real token.
            return new LoginResponse(true, null);
        }

        String token = jwtService.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new LoginResponse(false, new AuthResponse(token, user.getUserId(), user.getUsername()));
    }

    private void validatePasswordStrength(String password) {
        if (password == null
                || password.length() < 8
                || !UPPERCASE.matcher(password).find()
                || !LOWERCASE.matcher(password).find()
                || !DIGIT.matcher(password).find()) {
            throw new WeakPasswordException();
        }
    }
}
