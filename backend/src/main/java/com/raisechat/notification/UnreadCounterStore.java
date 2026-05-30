package com.raisechat.notification;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 未読カウントの Redis 表現を集約するストア。
 *
 * <p>キー設計: ユーザーごとに 2 つの Hash を持つ。
 * <ul>
 *   <li>{@code unread:{userId}} … 総未読数（全メッセージ）</li>
 *   <li>{@code mention:{userId}} … 未読メンション数（明示的な {@code @} のみ）</li>
 * </ul>
 * どちらもフィールドは {@code channel:{id}} / {@code dm:{id}} で共通（Hash が別なので衝突しない）。
 * <p>{@code _synced} マーカーは {@code unread:{userId}} 側だけに置き、両 Hash 共通の
 * 「Postgres から構築済みか」のゲートとして使う。マーカーが無ければ rebuild を発火させ、
 * その際に両 Hash を同時に再構築する。
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

    static String mentionKey(Long userId) {
        return "mention:" + userId;
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

    /** メンションファンアウト: メンション先だけ 1 件加算して加算後の値を返す。 */
    public long incrementMention(Long userId, String field) {
        Long value = redis.opsForHash().increment(mentionKey(userId), field, 1L);
        return value != null ? value : 0L;
    }

    /** 既読処理後の残りメンション数を明示的に設定する。 */
    public void setMention(Long userId, String field, long count) {
        redis.opsForHash().put(mentionKey(userId), field, String.valueOf(count));
    }

    /** 現在のメンション数を 1 件取得（未設定なら 0）。非メンション受信者の WS イベントに現在値を載せる用。 */
    public long getMention(Long userId, String field) {
        Object value = redis.opsForHash().get(mentionKey(userId), field);
        return value == null ? 0L : parseLong(value);
    }

    /** rebuild 結果を両 Hash へ一括書き込みし、構築済みマーカーを立てる（マーカーは unread 側のみ）。 */
    public void writeSnapshot(Long userId, Map<String, Long> unreadCounts, Map<String, Long> mentionCounts) {
        Map<String, String> unreadHash = new HashMap<>();
        unreadCounts.forEach((field, count) -> unreadHash.put(field, String.valueOf(count)));
        unreadHash.put(SYNC_MARKER_FIELD, "1");
        redis.opsForHash().putAll(key(userId), unreadHash);

        if (!mentionCounts.isEmpty()) {
            Map<String, String> mentionHash = new HashMap<>();
            mentionCounts.forEach((field, count) -> mentionHash.put(field, String.valueOf(count)));
            redis.opsForHash().putAll(mentionKey(userId), mentionHash);
        }
    }

    /** 現在の総未読数をすべて取得（マーカーは除外）。 */
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

    /** 現在のメンション数をすべて取得。 */
    public Map<String, Long> getAllMentions(Long userId) {
        Map<Object, Object> raw = redis.opsForHash().entries(mentionKey(userId));
        Map<String, Long> result = new HashMap<>();
        raw.forEach((field, value) -> result.put(String.valueOf(field), parseLong(value)));
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
