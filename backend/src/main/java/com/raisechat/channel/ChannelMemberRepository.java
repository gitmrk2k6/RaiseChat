package com.raisechat.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    // 現在アクティブなメンバー（left_at IS NULL）の判定用
    Optional<ChannelMember> findByChannelIdAndUserIdAndLeftAtIsNull(Long channelId, Long userId);

    // 新着メッセージのファンアウト先: チャンネルのアクティブメンバーの userId 一覧
    @Query("""
            SELECT cm.user.id FROM ChannelMember cm
            WHERE cm.channel.id = :channelId
              AND cm.leftAt IS NULL
            """)
    List<Long> findActiveUserIdsByChannelId(@Param("channelId") Long channelId);

    // メンバー一覧表示用: チャンネルのアクティブメンバーを user 込みで参加順に取得する。
    @Query("""
            SELECT cm FROM ChannelMember cm
            JOIN FETCH cm.user u
            WHERE cm.channel.id = :channelId
              AND cm.leftAt IS NULL
            ORDER BY cm.joinedAt ASC, cm.id ASC
            """)
    List<ChannelMember> findActiveByChannelIdWithUser(@Param("channelId") Long channelId);

    // 未読再構築用: ユーザーが現在参加しているチャンネルの id 一覧
    @Query("""
            SELECT cm.channel.id FROM ChannelMember cm
            WHERE cm.user.id = :userId
              AND cm.leftAt IS NULL
              AND cm.channel.deletedAt IS NULL
            """)
    List<Long> findActiveChannelIdsByUserId(@Param("userId") Long userId);

    // 再参加対応: 過去に退出した行（left_at IS NOT NULL）を取り直して left_at をクリアするため、
    // 状態に依らず (channel_id, user_id) で 1 件取得する（DB のユニーク制約と整合）
    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);

    // F-16 キック用: 当該ワークスペース内で、対象ユーザーが現在参加している全チャンネルのメンバー行。
    // left_at セット（論理退出）と Redis クリア（channel.id 利用）の両方に使う。
    @Query("""
            SELECT cm FROM ChannelMember cm
            JOIN FETCH cm.channel c
            WHERE c.workspace.id = :workspaceId
              AND cm.user.id = :userId
              AND cm.leftAt IS NULL
            """)
    List<ChannelMember> findActiveByWorkspaceIdAndUserId(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId);

    // F-16 ワークスペース削除用: 当該ワークスペース内の全アクティブなチャンネルメンバー行。
    // left_at セット（論理退出）と Redis クリア（user.id / channel.id 利用）の両方に使う。
    @Query("""
            SELECT cm FROM ChannelMember cm
            JOIN FETCH cm.channel c
            JOIN FETCH cm.user u
            WHERE c.workspace.id = :workspaceId
              AND cm.leftAt IS NULL
            """)
    List<ChannelMember> findActiveByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
