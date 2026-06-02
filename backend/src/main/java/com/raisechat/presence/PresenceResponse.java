package com.raisechat.presence;

import java.util.List;

/**
 * presence seed のレスポンス。現在オンラインなユーザーの数値 id 一覧。
 *
 * @param userIds オンラインユーザーの数値 id（フロントは String 化して保持・突き合わせる）
 */
public record PresenceResponse(
        List<Long> userIds
) {
}
