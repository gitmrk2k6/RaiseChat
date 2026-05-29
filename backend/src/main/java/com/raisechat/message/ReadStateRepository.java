package com.raisechat.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadStateRepository extends JpaRepository<ReadState, Long> {

    // 既読位置の upsert 用。(user_id, channel_id) / (user_id, dm_room_id) は DB 側でユニーク。
    Optional<ReadState> findByUserIdAndChannelId(Long userId, Long channelId);

    Optional<ReadState> findByUserIdAndDmRoomId(Long userId, Long dmRoomId);

    // rebuild 時に、ユーザーの全既読位置をまとめて引く（チャンネル / DM それぞれ）。
    List<ReadState> findByUserIdAndChannelIdIsNotNull(Long userId);

    List<ReadState> findByUserIdAndDmRoomIdIsNotNull(Long userId);
}
