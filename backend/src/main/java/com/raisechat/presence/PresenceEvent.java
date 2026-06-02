package com.raisechat.presence;

/**
 * オンライン状態（presence）の変化イベント。Slack 流にユーザー単位グローバル。
 *
 * <p>typing と違い presence は状態を持つ（{@link PresenceService} が Redis Set で管理）。
 * このイベントは on/off が切り替わった瞬間（0→1 / 1→0）だけ発火し、Redis pub/sub
 * （{@code presence:changed}）のペイロードとしてそのまま使う。各インスタンスの
 * {@link PresenceRedisSubscriber} が {@code /topic/presence} へ中継する（冗長化対応）。
 *
 * @param userId 状態が変化したユーザーの数値 id（受信側で自分判定・突き合わせに使う）
 * @param online true=オンライン / false=オフライン
 */
public record PresenceEvent(
        Long userId,
        boolean online
) {
}
