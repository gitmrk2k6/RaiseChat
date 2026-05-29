package com.raisechat.notification;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 未読カウントの Redis 表現を集約するストア。
 *
 * <p>キー設計: ユーザーごとに 1 つの Hash {@code unread:{userId}} を持ち、
 * フィールドは {@code channel:{id}} / {@code dm:{id}}、値が未読数。
 * <p>{@code _synced} マーカーで「まだ Postgres から構築していないコールド状態」と
 * 「構築済みだが全部既読でゼロ」を区別する。マーカーが無ければ rebuild を発火させる。
 */
@Component
public class UnreadCounterStore {

    static final String SYNC_MARKER_FIELD = "_synced";

    private final StringRedisTemplate redis;

    public UnreadCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(Long userId) {
        return "unread:" + userId;
    }

    public static String channelField(Long channelId) {
        return "channel:" + channelId;
    }

    public static String dmField(Long dmRoomId) {
        return "dm:" + dmRoomId;
    }

    /** 新着メッセージのファンアウト: 1 件加算して加算後の値を返す。 */
    public long increment(Long userId, String field) {
        Long value = redis.opsForHash().increment(key(userId), field, 1L);
        return value != null ? value : 0L;
    }

    /** 既読処理後の残り未読数を明示的に設定する（通常は 0、部分既読なら残数）。 */
    public void set(Long userId, String field, long count) {
        redis.opsForHash().put(key(userId), field, String.valueOf(count));
    }

    /** マーカーが存在するか（= Postgres から構築済みか）。 */
    public boolean isSynced(Long userId) {
        return redis.opsForHash().hasKey(key(userId), SYNC_MARKER_FIELD);
    }

    /** rebuild 結果を一括書き込みし、構築済みマーカーを立てる。 */
    public void writeSnapshot(Long userId, Map<String, Long> counts) {
        Map<String, String> hash = new HashMap<>();
        counts.forEach((field, count) -> hash.put(field, String.valueOf(count)));
        hash.put(SYNC_MARKER_FIELD, "1");
        redis.opsForHash().putAll(key(userId), hash);
    }

    /** 現在のカウントをすべて取得（マーカーは除外）。 */
    public Map<String, Long> getAll(Long userId) {
        Map<Object, Object> raw = redis.opsForHash().entries(key(userId));
        Map<String, Long> result = new HashMap<>();
        raw.forEach((field, value) -> {
            String f = String.valueOf(field);
            if (SYNC_MARKER_FIELD.equals(f)) {
                return;
            }
            result.put(f, parseLong(value));
        });
        return result;
    }

    private long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
