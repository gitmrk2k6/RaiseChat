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

    List<Message> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long parentId);
}
