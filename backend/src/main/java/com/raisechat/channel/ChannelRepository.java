package com.raisechat.channel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByIdAndDeletedAtIsNull(Long id);

    // ワークスペース内の同名チャンネル重複検査（大文字小文字を無視。DB のユニーク制約と同じ意味）
    boolean existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);

    // ワークスペース内の特定チャンネルを名前で取得（招待受諾時に "general" へ自動参加させるため）。
    // チャンネル名は ws 内で case-insensitive ユニークなので 1 件に特定できる。
    Optional<Channel> findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);

    // 一覧クエリ:
    //   - 同 ws のメンバー前提（service 層で検証済み）
    //   - PUBLIC は無条件で表示、PRIVATE は自分が channel_members に含まれる場合のみ表示
    //   - type フィルタ: null なら全件、指定があればその type のみ
    //   - joined=true なら自分が参加済みのチャンネルのみ
    //   - cursor ベース: id 昇順、id > cursor
    @Query("""
            SELECT c FROM Channel c
            JOIN FETCH c.createdBy
            JOIN FETCH c.workspace
            WHERE c.workspace.id = :workspaceId
              AND c.deletedAt IS NULL
              AND c.id > :cursor
              AND (:type IS NULL OR c.type = :type)
              AND (
                    c.type = com.raisechat.channel.ChannelType.PUBLIC
                    OR EXISTS (SELECT 1 FROM ChannelMember cm
                               WHERE cm.channel = c
                                 AND cm.user.id = :userId
                                 AND cm.leftAt IS NULL)
                  )
              AND (
                    :joined = false
                    OR EXISTS (SELECT 1 FROM ChannelMember cm2
                               WHERE cm2.channel = c
                                 AND cm2.user.id = :userId
                                 AND cm2.leftAt IS NULL)
                  )
            ORDER BY c.id ASC
            """)
    List<Channel> findVisibleAfterCursor(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("type") ChannelType type,
            @Param("joined") boolean joined,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
