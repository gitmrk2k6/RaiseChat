package com.raisechat.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    // 編集時の再同期で既存メンションを一掃するために使う（新規送信時は no-op）。
    @Modifying
    @Query("DELETE FROM Mention m WHERE m.message.id = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);

    // 1 メッセージのメンション先 userId 一覧（保存直後の返却用）。
    @Query("SELECT m.mentionedUser.id FROM Mention m WHERE m.message.id = :messageId")
    List<Long> findMentionedUserIdsByMessageId(@Param("messageId") Long messageId);

    // リスト取得用: 複数メッセージのメンションを 1 クエリで取得し、サービス側で messageId ごとに束ねる。
    @Query("""
            SELECT m.message.id AS messageId, m.mentionedUser.id AS userId
            FROM Mention m
            WHERE m.message.id IN :messageIds
            """)
    List<MentionUserView> findByMessageIdIn(@Param("messageIds") List<Long> messageIds);

    interface MentionUserView {
        Long getMessageId();
        Long getUserId();
    }
}
