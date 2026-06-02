package com.raisechat.message.dto;

/**
 * 入力中（typing）インジケータのイベント。短命・高頻度・揮発な性質のため永続化しない。
 *
 * <p>Redis pub/sub（typing:channel:{id} / typing:dm:{id}）のペイロードとしてそのまま使い、
 * 各インスタンスの {@link com.raisechat.message.ws.TypingRedisSubscriber} が
 * /topic/channels/{id}/typing・/topic/dm/{id}/typing へ中継する。
 *
 * @param userId      入力中ユーザーの数値 id（受信側で自分を除外するのに使う）
 * @param displayName 表示名（「○○ が入力中…」用）
 */
public record TypingEvent(
        Long userId,
        String displayName
) {
}
