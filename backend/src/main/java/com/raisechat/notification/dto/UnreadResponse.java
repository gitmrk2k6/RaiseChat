package com.raisechat.notification.dto;

import java.util.List;

/** GET /api/notifications/unread のレスポンス。未読が 0 件のスコープは含めない。 */
public record UnreadResponse(
        List<UnreadItem> items
) {
}
