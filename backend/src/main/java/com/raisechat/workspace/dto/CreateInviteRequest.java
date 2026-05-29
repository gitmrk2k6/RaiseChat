package com.raisechat.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 招待リンク発行リクエスト。両フィールドとも任意（空ボディ {} を許容）。
public record CreateInviteRequest(
        // 有効期限（時間）。未指定なら既定 168 時間 = 7 日。1 時間〜1 年(8760h)。
        @Min(value = 1, message = "expiresInHours は 1 以上")
        @Max(value = 8760, message = "expiresInHours は 8760 以下")
        Integer expiresInHours,

        // 使用回数の上限。null = 無制限。1〜1000。
        @Min(value = 1, message = "maxUses は 1 以上")
        @Max(value = 1000, message = "maxUses は 1000 以下")
        Integer maxUses
) {
    public static final int DEFAULT_EXPIRES_IN_HOURS = 168;

    public int resolveExpiresInHours() {
        return expiresInHours == null ? DEFAULT_EXPIRES_IN_HOURS : expiresInHours;
    }
}
