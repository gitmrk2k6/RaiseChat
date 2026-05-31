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

    // F-13 メッセージ全文検索。body_tsv（to_tsvector('simple', body) の STORED 生成列、GIN インデックス）に
    // websearch_to_tsquery で問い合わせる。websearch_* はユーザー入力をそのまま渡しても構文エラーにならない。
    //
    // 検索範囲は呼び出しユーザーが閲覧可能なメッセージのみに絞る:
    //   - channel: 当該ユーザーが現在参加している（channel_members.left_at IS NULL）チャンネル
    //   - dm:      当該ユーザーが当事者（user_a / user_b）である未削除の DM ルーム
    // 並び順は id DESC（新着順）。id カーソル（cursorId）で「それより古い」ページを返す。
    // スレッド返信（parent_message_id IS NOT NULL）も検索対象に含める（過去の議論の再利用が目的のため）。
    @Query(value = """
            SELECT m.id                 AS id,
                   m.channel_id          AS channelId,
                   c.name                AS channelName,
                   m.dm_room_id          AS dmRoomId,
                   m.parent_message_id   AS parentMessageId,
                   u.id                  AS authorId,
                   u.user_id             AS authorUserId,
                   u.display_name        AS authorDisplayName,
                   m.body                AS body,
                   m.edited_at           AS editedAt,
                   m.created_at          AS createdAt
            FROM messages m
            JOIN users u ON u.id = m.author_user_id
            LEFT JOIN channels c ON c.id = m.channel_id
            WHERE m.workspace_id = :workspaceId
              AND m.deleted_at IS NULL
              AND m.body_tsv @@ websearch_to_tsquery('simple', :q)
              AND (:cursorId = 0 OR m.id < :cursorId)
              AND (
                (m.channel_id IS NOT NULL AND m.channel_id IN (
                    SELECT cm.channel_id FROM channel_members cm
                    WHERE cm.user_id = :userId AND cm.left_at IS NULL
                ))
                OR
                (m.dm_room_id IS NOT NULL AND m.dm_room_id IN (
                    SELECT d.id FROM dm_rooms d
                    WHERE d.deleted_at IS NULL
                      AND (d.user_a_id = :userId OR d.user_b_id = :userId)
                ))
              )
            ORDER BY m.id DESC
            """, nativeQuery = true)
    List<MessageSearchRow> searchInWorkspace(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("q") String q,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
