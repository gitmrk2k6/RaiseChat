package com.raisechat.workspace.exception;

// 招待は存在するが状態的に利用できない（期限切れ / 無効化済み / 使用上限到達）→ 410 Gone。
// リクエスト自体は妥当なため 422(バリデーション)ではなく 410 を用いる。
public class InviteGoneException extends RuntimeException {
    public InviteGoneException(String message) {
        super(message);
    }
}
