package com.raisechat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.author
            WHERE m.channel.id = :channelId
              AND m.deletedAt IS NULL
              AND m.parent IS NULL
              AND (:cursorId = 0 OR m.id < :cursorId)
            ORDER BY m.id DESC
            """)
    List<Message> findChannelMessagesBefore(
            @Param("channelId") Long channelId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.author
            WHERE m.dmRoom.id = :dmRoomId
              AND m.deletedAt IS NULL
              AND m.parent IS NULL
              AND (:cursorId = 0 OR m.id < :cursorId)
            ORDER BY m.id DESC
            """)
    List<Message> findDmMessagesBefore(
            @Param("dmRoomId") Long dmRoomId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.author
            WHERE m.parent.id = :parentId
              AND m.deletedAt IS NULL
              AND (:cursorId = 0 OR m.id > :cursorId)
            ORDER BY m.id ASC
            """)
    List<Message> findRepliesAfter(
            @Param("parentId") Long parentId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // 未読数の再構築（cache-aside のミス時）。
    // 既読位置より新しく、自分以外が投稿した未削除メッセージを数える。スレッド返信も対象に含める。
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.channel.id = :channelId
              AND m.deletedAt IS NULL
              AND m.author.id <> :userId
              AND m.id > :lastReadMessageId
            """)
    long countChannelUnread(
            @Param("channelId") Long channelId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") long lastReadMessageId);

    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.dmRoom.id = :dmRoomId
              AND m.deletedAt IS NULL
              AND m.author.id <> :userId
              AND m.id > :lastReadMessageId
            """)
    long countDmUnread(
            @Param("dmRoomId") Long dmRoomId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") long lastReadMessageId);

    // 既読位置を省略して「最新まで既読」にする場合の解決用。メッセージが無ければ null。
    @Query("SELECT MAX(m.id) FROM Message m WHERE m.channel.id = :channelId AND m.deletedAt IS NULL")
    Long findMaxIdByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT MAX(m.id) FROM Message m WHERE m.dmRoom.id = :dmRoomId AND m.deletedAt IS NULL")
    Long findMaxIdByDmRoomId(@Param("dmRoomId") Long dmRoomId);
}
