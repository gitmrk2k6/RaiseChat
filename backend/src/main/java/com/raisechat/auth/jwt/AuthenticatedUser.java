package com.raisechat.auth.jwt;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 認証済みユーザーの最小表現。REST では {@code @AuthenticationPrincipal} で受け取り id()/userId() を使う。
 *
 * <p>{@link AuthenticatedPrincipal} を実装し getName() に「数値 id」を返す。これは WebSocket の
 * ユーザー個別配信（F-14: convertAndSendToUser は notifications:{numericId} に合わせ数値 id を user 名に使う）で
 * セッションを正しく引き当てるために必要。Spring Security の Authentication#getName もこの値になる。
 */
public record AuthenticatedUser(Long id, String userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}
