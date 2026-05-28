package com.raisechat.channel;

import com.raisechat.channel.dto.ChannelListResponse;
import com.raisechat.channel.dto.ChannelResponse;
import com.raisechat.channel.dto.CreateChannelRequest;
import com.raisechat.channel.exception.ChannelConflictException;
import com.raisechat.channel.exception.ChannelForbiddenException;
import com.raisechat.channel.exception.ChannelNotFoundException;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.Workspace;
import com.raisechat.workspace.WorkspaceMember;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRepository;
import com.raisechat.workspace.WorkspaceRole;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChannelService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    // 退出禁止 / 削除禁止のシステム予約名
    private static final String GENERAL_CHANNEL_NAME = "general";

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChannelResponse create(Long userId, Long workspaceId, CreateChannelRequest req) {
        Workspace ws = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        requireWorkspaceMember(workspaceId, userId);

        if (channelRepository.existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, req.name())) {
            throw new ChannelConflictException("同名のチャンネルが既に存在します: " + req.name());
        }

        User creator = userRepository.getReferenceById(userId);

        Channel channel = new Channel();
        channel.setWorkspace(ws);
        channel.setName(req.name());
        channel.setDescription(req.description() == null ? "" : req.description());
        channel.setType(req.type());
        channel.setCreatedBy(creator);
        channelRepository.saveAndFlush(channel);

        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(creator);
        channelMemberRepository.saveAndFlush(member);

        // created_at / updated_at は DB デフォルトで設定されるため refresh で読み戻す
        entityManager.refresh(channel);
        return ChannelResponse.from(channel);
    }

    @Transactional(readOnly = true)
    public ChannelListResponse list(
            Long userId,
            Long workspaceId,
            ChannelType type,
            Boolean joined,
            String cursor,
            Integer limit
    ) {
        workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        requireWorkspaceMember(workspaceId, userId);

        int effectiveLimit = clampLimit(limit);
        Long cursorId = parseCursor(cursor);
        boolean joinedOnly = Boolean.TRUE.equals(joined);

        Pageable pageable = PageRequest.ofSize(effectiveLimit + 1);
        List<Channel> rows = channelRepository.findVisibleAfterCursor(
                workspaceId, userId, type, joinedOnly, cursorId, pageable);

        boolean hasMore = rows.size() > effectiveLimit;
        List<Channel> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<ChannelResponse> items = page.stream().map(ChannelResponse::from).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new ChannelListResponse(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public ChannelResponse getDetail(Long userId, Long channelId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        requireChannelVisible(channel, userId);
        return ChannelResponse.from(channel);
    }

    @Transactional
    public ChannelResponse join(Long userId, Long channelId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        requireWorkspaceMember(channel.getWorkspace().getId(), userId);

        if (channel.getType() == ChannelType.PRIVATE) {
            // 招待ベースの参加は F-16（招待）で対応。現状の自己 join は PUBLIC のみ
            throw new ChannelForbiddenException(
                    "プライベートチャンネルへは招待なしで参加できません: channelId=" + channelId);
        }

        // 過去に退出履歴があれば left_at をクリアして再参加。なければ新規 INSERT
        channelMemberRepository.findByChannelIdAndUserId(channelId, userId).ifPresentOrElse(existing -> {
            if (existing.getLeftAt() == null) {
                throw new ChannelConflictException("既にチャンネルに参加しています: channelId=" + channelId);
            }
            existing.setLeftAt(null);
        }, () -> {
            ChannelMember member = new ChannelMember();
            member.setChannel(channel);
            member.setUser(userRepository.getReferenceById(userId));
            channelMemberRepository.save(member);
        });

        return ChannelResponse.from(channel);
    }

    @Transactional
    public void leave(Long userId, Long channelId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        if (GENERAL_CHANNEL_NAME.equalsIgnoreCase(channel.getName())) {
            throw new ChannelConflictException("general チャンネルからは退出できません");
        }

        ChannelMember member = channelMemberRepository
                .findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .orElseThrow(() -> new ChannelConflictException(
                        "チャンネルに参加していません: channelId=" + channelId));

        member.setLeftAt(OffsetDateTime.now());
    }

    @Transactional
    public void delete(Long userId, Long channelId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        WorkspaceMember wsMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserIdAndLeftAtIsNull(channel.getWorkspace().getId(), userId)
                .orElseThrow(() -> new ChannelForbiddenException(
                        "ワークスペースのメンバーではありません: channelId=" + channelId));

        boolean isOwner = wsMember.getRole() == WorkspaceRole.OWNER;
        boolean isCreator = channel.getCreatedBy().getId().equals(userId);
        if (!isOwner && !isCreator) {
            throw new ChannelForbiddenException(
                    "OWNER または作成者のみチャンネルを削除できます: channelId=" + channelId);
        }

        if (GENERAL_CHANNEL_NAME.equalsIgnoreCase(channel.getName())) {
            throw new ChannelConflictException("general チャンネルは削除できません");
        }

        channel.setDeletedAt(OffsetDateTime.now());
    }

    private void requireWorkspaceMember(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceForbiddenException(workspaceId, userId));
    }

    private void requireChannelVisible(Channel channel, Long userId) {
        // PUBLIC: 同 ws のメンバーなら閲覧可
        // PRIVATE: 当該チャンネルのメンバーのみ
        requireWorkspaceMember(channel.getWorkspace().getId(), userId);
        if (channel.getType() == ChannelType.PRIVATE) {
            channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channel.getId(), userId)
                    .orElseThrow(() -> new ChannelForbiddenException(
                            "プライベートチャンネルのメンバーではありません: channelId=" + channel.getId()));
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0L;
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
