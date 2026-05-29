package com.raisechat.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    // 1 メッセージ・1 emoji を付与したユーザー ID を古い順で取得。件数も size() で兼ねる。
    @Query("SELECT r.user.id FROM Reaction r "
            + "WHERE r.message.id = :messageId AND r.emoji = :emoji ORDER BY r.id ASC")
    List<Long> findUserIdsByMessageIdAndEmoji(@Param("messageId") Long messageId, @Param("emoji") String emoji);
}
