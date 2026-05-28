package com.raisechat.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    // 現在アクティブなメンバー（left_at IS NULL）の判定用
    Optional<ChannelMember> findByChannelIdAndUserIdAndLeftAtIsNull(Long channelId, Long userId);

    // 再参加対応: 過去に退出した行（left_at IS NOT NULL）を取り直して left_at をクリアするため、
    // 状態に依らず (channel_id, user_id) で 1 件取得する（DB のユニーク制約と整合）
    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);
}
