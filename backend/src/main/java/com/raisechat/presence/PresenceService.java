package com.raisechat.presence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * オンライン状態（presence）を Redis で管理するサービス。Slack 流にユーザー単位グローバル。
 *
 * <p>状態の持ち方は <b>参照カウント</b>。{@code presence:user:{userId}} を Set にし、要素に
 * STOMP セッションIDを持つ。1 ユーザーが複数タブ（=複数セッション）を開いても、最後の 1 つが
 * 切れて初めて offline になる。状態を Redis に置くことで複数インスタンスでも一貫する。
 *
 * <p>on/off が切り替わった瞬間（空→1 / 1→空）だけ {@link PresenceEvent} を Redis pub/sub へ
 * publish する。各インスタンスの {@link PresenceRedisSubscriber} が {@code /topic/presence} へ中継。
 *
 * <p><b>幽霊オンライン対策（軽い保険のみ）</b>: サーバが正常停止せず DISCONNECT が飛ばないと Set に
 * ゴミが残る。これに対し (1) 接続のたびにキーへ保険 TTL を打ち直す、(2) アプリ起動時に presence
 * キーを一掃する、の 2 点だけ行う（スケジューラによる延命はしない）。
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.presence-channel:presence:changed}")
    private String presenceChannel;

    @Value("${app.redis.presence-key-prefix:presence:user:}")
    private String keyPrefix;

    /** 接続のたびに打ち直す保険 TTL（既定 12 時間）。正常な DISCONNECT があれば普通はここまで残らない。 */
    @Value("${app.presence.ttl-seconds:43200}")
    private long ttlSeconds;

    public PresenceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** アプリ起動時に前プロセスの残骸（幽霊オンライン）を一掃する。 */
    @EventListener(ApplicationReadyEvent.class)
    public void clearOnStartup() {
        Set<String> keys = redisTemplate.keys(keyPrefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} stale presence key(s) on startup", keys.size());
        }
    }

    /**
     * セッション接続を記録する。空→1 になった瞬間だけ ONLINE を発火する。
     * いずれの場合も保険 TTL を打ち直す。
     */
    public void connected(Long userId, String sessionId) {
        String key = keyPrefix + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        Long size = redisTemplate.opsForSet().size(key);
        if (size != null && size == 1L) {
            publish(new PresenceEvent(userId, true));
        }
    }

    /**
     * セッション切断を記録する。1→空 になった瞬間だけ OFFLINE を発火しキーを削除する。
     */
    public void disconnected(Long userId, String sessionId) {
        String key = keyPrefix + userId;
        redisTemplate.opsForSet().remove(key, sessionId);
        Long size = redisTemplate.opsForSet().size(key);
        if (size == null || size == 0L) {
            redisTemplate.delete(key);
            publish(new PresenceEvent(userId, false));
        }
    }

    /** 現在オンラインなユーザーの数値 id 一覧（seed 用）。キーが残っている=メンバーが居る、で判定する。 */
    public List<Long> onlineUserIds() {
        Set<String> keys = redisTemplate.keys(keyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> k.substring(keyPrefix.length()))
                .map(Long::valueOf)
                .toList();
    }

    private void publish(PresenceEvent event) {
        try {
            redisTemplate.convertAndSend(presenceChannel, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Presence publish failed for userId={}: {}", event.userId(), e.getMessage(), e);
        }
    }
}
