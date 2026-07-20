package com.neo4flix.user_service.service;

import com.neo4flix.user_service.domain.User;
import com.neo4flix.user_service.dto.AuthResponse;
import com.neo4flix.user_service.dto.TwoFactorSetupResponse;
import com.neo4flix.user_service.exception.InvalidCredentialsException;
import com.neo4flix.user_service.exception.InvalidTotpCodeException;
import com.neo4flix.user_service.exception.UserNotFoundException;
import com.neo4flix.user_service.repository.UserRepository;
import com.neo4flix.user_service.security.JwtService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TwoFactorService {

    private static final String ISSUER = "Neo4flix";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    public TwoFactorService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public TwoFactorSetupResponse setup(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String secret = secretGenerator.generate();
        user.setTotpSecret(secret);
        userRepository.save(user);

        QrData data = new QrData.Builder()
                .label(user.getUsername())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] imageData = qrGenerator.generate(data);
            String dataUri = Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());
            return new TwoFactorSetupResponse(secret, dataUri);
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Could not generate the 2FA QR code", e);
        }
    }

    public void enable(String userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("Call /api/auth/2fa/setup before enabling 2FA");
        }
        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new InvalidTotpCodeException();
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    public void disable(String userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new InvalidTotpCodeException();
        }

        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse verifyLogin(String username, String code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isTwoFactorEnabled() || user.getTotpSecret() == null) {
            throw new InvalidCredentialsException();
        }
        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new InvalidTotpCodeException();
        }

        String token = jwtService.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new AuthResponse(token, user.getUserId(), user.getUsername());
    }
}
