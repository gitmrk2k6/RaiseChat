package com.raisechat.workspace;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// 招待トークンの生成・ハッシュ化。
// 平文トークンはクライアントへ一度だけ返し、DB には SHA-256 ハッシュ(hex 64桁)のみ保存する。
// 実装方針は auth の JwtService#generateRefreshToken / hashRefreshToken と同一だが、
// 認証ドメイン(auth)へ機能層(workspace)を結合させないため別コンポーネントとして持つ。
@Component
public class InviteTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    // SecureRandom 32 バイト → Base64 URL-safe（パディング無）。URL に直接載せられる ~43 文字の平文。
    public String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 → hex 64 桁。token_hash VARCHAR(64) に一致。
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
