package com.raisechat.auth;

import com.raisechat.auth.dto.LoginRequest;
import com.raisechat.auth.dto.MeResponse;
import com.raisechat.auth.dto.RefreshRequest;
import com.raisechat.auth.dto.SignupRequest;
import com.raisechat.auth.dto.TokenResponse;
import com.raisechat.auth.exception.InvalidCredentialsException;
import com.raisechat.auth.exception.InvalidRefreshTokenException;
import com.raisechat.auth.exception.UserAlreadyExistsException;
import com.raisechat.auth.jwt.JwtService;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse signup(SignupRequest req) {
        if (userRepository.existsByUserId(req.userId())) {
            throw new UserAlreadyExistsException("userId はすでに使われています");
        }
        User user = new User();
        user.setUserId(req.userId());
        user.setDisplayName(req.displayName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUserId(req.userId())
                .orElseThrow(() -> new InvalidCredentialsException("userId またはパスワードが正しくありません"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("userId またはパスワードが正しくありません");
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        String hash = jwtService.hashRefreshToken(req.refreshToken());
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("無効なリフレッシュトークンです"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!token.isActive(now)) {
            throw new InvalidRefreshTokenException("無効なリフレッシュトークンです");
        }
        token.setRevokedAt(now);
        refreshTokenRepository.save(token);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("無効なリフレッシュトークンです"));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userIdPk) {
        User user = userRepository.findById(userIdPk)
                .orElseThrow(() -> new InvalidCredentialsException("ユーザが存在しません"));
        return new MeResponse(
                user.getId(),
                user.getUserId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatusMessage()
        );
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshToken();
        String hash = jwtService.hashRefreshToken(rawRefresh);

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(jwtService.refreshTtlSeconds());
        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenHash(hash);
        entity.setExpiresAt(expiresAt);
        refreshTokenRepository.save(entity);

        return new TokenResponse(accessToken, rawRefresh, jwtService.accessTtlSeconds());
    }
}
