package com.raisechat.channel;

import com.raisechat.channel.dto.ChannelInviteResponse;
import com.raisechat.channel.dto.ChannelListResponse;
import com.raisechat.channel.dto.ChannelMemberResponse;
import com.raisechat.channel.dto.ChannelResponse;
import com.raisechat.channel.dto.CreateChannelRequest;
import com.raisechat.channel.exception.ChannelConflictException;
import com.raisechat.channel.exception.ChannelForbiddenException;
import com.raisechat.channel.exception.ChannelNotFoundException;
import com.raisechat.notification.NotificationPublisher;
import com.raisechat.notification.UnreadCounterStore;
import com.raisechat.notification.dto.NotificationEvent;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.InviteTokenService;
import com.raisechat.workspace.Workspace;
import com.raisechat.workspace.WorkspaceMember;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRepository;
import com.raisechat.workspace.WorkspaceRole;
import com.raisechat.workspace.dto.CreateInviteRequest;
import com.raisechat.workspace.exception.InviteGoneException;
import com.raisechat.workspace.exception.InviteNotFoundException;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
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
    private final ChannelInviteRepository channelInviteRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final InviteTokenService inviteTokenService;
    private final UnreadCounterStore unreadCounterStore;
    private final NotificationPublisher notificationPublisher;

    // 招待 URL の組み立てに使うフロントエンドのベース URL（ワークスペース招待とは別経路）。
    @Value("${app.channel-invite.base-url:http://localhost:3000/channel-invite}")
    private String channelInviteBaseUrl;

    @PersistenceContext
    private EntityManager entityManager;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelInviteRepository channelInviteRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            InviteTokenService inviteTokenService,
            UnreadCounterStore unreadCounterStore,
            NotificationPublisher notificationPublisher
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.channelInviteRepository = channelInviteRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.inviteTokenService = inviteTokenService;
        this.unreadCounterStore = unreadCounterStore;
        this.notificationPublisher = notificationPublisher;
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

        // 削除確定の前にアクティブメンバーを確定させる（left_at で絞るため deleted_at の影響は受けない）。
        List<Long> memberIds = channelMemberRepository.findActiveUserIdsByChannelId(channelId);

        channel.setDeletedAt(OffsetDateTime.now());

        // 各メンバーの未読バッジを消し、画面から当該チャンネルを即座に消すため通知を発行する。
        // WS キック / WS 削除と同じ後始末で幽霊バッジを残さない。
        String field = UnreadCounterStore.channelField(channelId);
        for (Long memberId : memberIds) {
            unreadCounterStore.clear(memberId, field);
            notificationPublisher.publish(memberId, NotificationEvent.channelRemoved(channelId));
        }
    }

    // ---------- 招待 (F-15: チャンネル招待) ----------

    // 招待リンクを発行する。発行できるのはチャンネルのアクティブメンバーのみ。
    // 平文トークンはこのレスポンスでのみ返す（DB にはハッシュしか残らない）。
    @Transactional
    public ChannelInviteResponse createInvite(Long userId, Long channelId, CreateInviteRequest req) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));
        requireChannelMember(channelId, userId);

        String rawToken = inviteTokenService.generateRawToken();

        ChannelInvite invite = new ChannelInvite();
        invite.setChannel(channel);
        invite.setInvitedBy(userRepository.getReferenceById(userId));
        invite.setTokenHash(inviteTokenService.hash(rawToken));
        invite.setExpiresAt(OffsetDateTime.now().plusHours(req.resolveExpiresInHours()));
        invite.setMaxUses(req.maxUses());
        channelInviteRepository.saveAndFlush(invite);

        // created_at は DB デフォルトで設定されるため refresh で読み戻す。
        entityManager.refresh(invite);

        return ChannelInviteResponse.from(invite, rawToken, channelInviteBaseUrl + "/" + rawToken);
    }

    // 招待を受諾し、呼び出しユーザーをチャンネルのメンバーにする。
    // 受諾の前提として、対象チャンネルが属するワークスペースのメンバーであること（非メンバーは 403）。
    // 既にチャンネルのアクティブメンバーなら冪等に 200（used_count は増やさない）。
    @Transactional
    public ChannelResponse acceptInvite(Long userId, String rawToken) {
        ChannelInvite invite = channelInviteRepository.findByTokenHash(inviteTokenService.hash(rawToken))
                .orElseThrow(InviteNotFoundException::new);

        if (invite.getRevokedAt() != null) {
            throw new InviteGoneException("招待は無効化されています");
        }
        if (invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InviteGoneException("招待の有効期限が切れています");
        }
        if (invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new InviteGoneException("招待の使用上限に達しています");
        }

        Channel channel = invite.getChannel();
        if (channel.getDeletedAt() != null) {
            // チャンネルが削除済みなら招待は実質無効。トークン総当たり対策で 404 に倒す。
            throw new InviteNotFoundException();
        }

        // チャンネル参加にはワークスペースメンバーであることが前提（非メンバーは 403）。
        requireWorkspaceMember(channel.getWorkspace().getId(), userId);

        // 既にアクティブメンバーなら冪等に成功（使用回数も消費しない）。
        if (channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channel.getId(), userId).isPresent()) {
            return ChannelResponse.from(channel);
        }

        addAsChannelMember(channel, userId);

        // read-modify-write。WorkspaceService#acceptInvite と同様に高並行では max_uses を
        // わずかに超過しうるが、招待ユースケースでは許容。
        invite.setUsedCount(invite.getUsedCount() + 1);

        return ChannelResponse.from(channel);
    }

    /**
     * チャンネルへメンバーを直接追加する（InviteUserModal）。
     * 追加できるのは当該チャンネルのアクティブメンバーのみ。対象は同ワークスペースのメンバーであること。
     * 既にメンバーの場合や過去に退出した場合も {@link #addAsChannelMember} が冪等に処理する。
     */
    @Transactional
    public ChannelResponse addMembers(Long actorUserId, Long channelId, List<Long> userIds) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        // 追加操作はチャンネルメンバーのみ（招待リンク発行 createInvite と同じ権限モデル）。
        requireChannelMember(channelId, actorUserId);

        Long workspaceId = channel.getWorkspace().getId();
        for (Long targetUserId : userIds.stream().distinct().toList()) {
            // 追加対象はワークスペースメンバーであることが前提（非メンバーは 403）。
            requireWorkspaceMember(workspaceId, targetUserId);
            boolean added = addAsChannelMember(channel, targetUserId);
            // 新規にメンバー化した対象（自分以外）へ channelAdded を配信し、
            // 追加された側のサイドバーへリロード無しで即時反映する（キック channelRemoved と対称）。
            if (added && !targetUserId.equals(actorUserId)) {
                notificationPublisher.publish(targetUserId, NotificationEvent.channelAdded(channelId));
            }
        }

        return ChannelResponse.from(channel);
    }

    /** チャンネルのアクティブメンバー一覧を返す（チャンネルを閲覧できるユーザーのみ）。 */
    @Transactional(readOnly = true)
    public List<ChannelMemberResponse> listMembers(Long userId, Long channelId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        requireChannelVisible(channel, userId);

        return channelMemberRepository.findActiveByChannelIdWithUser(channelId).stream()
                .map(ChannelMemberResponse::from)
                .toList();
    }

    /**
     * チャンネルからメンバーを除外（キック）する。
     * 認可は OWNER または作成者（チャンネル削除と同じモデル）。general は除外不可・自分自身も不可。
     * 対象が現在のメンバーでなければ冪等に no-op。除外時は対象に channelRemoved 通知＋未読クリアで即時反映する。
     */
    @Transactional
    public void kickMember(Long actorUserId, Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));

        WorkspaceMember wsMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserIdAndLeftAtIsNull(channel.getWorkspace().getId(), actorUserId)
                .orElseThrow(() -> new ChannelForbiddenException(
                        "ワークスペースのメンバーではありません: channelId=" + channelId));

        boolean isOwner = wsMember.getRole() == WorkspaceRole.OWNER;
        boolean isCreator = channel.getCreatedBy().getId().equals(actorUserId);
        if (!isOwner && !isCreator) {
            throw new ChannelForbiddenException(
                    "OWNER または作成者のみメンバーを削除できます: channelId=" + channelId);
        }

        if (targetUserId.equals(actorUserId)) {
            throw new ChannelConflictException("自分自身は削除できません（退出を使ってください）");
        }

        if (GENERAL_CHANNEL_NAME.equalsIgnoreCase(channel.getName())) {
            throw new ChannelConflictException("general チャンネルからは削除できません");
        }

        // アクティブメンバーなら論理退出させ、対象の画面から当該チャンネルを即座に消す。
        // チャンネル削除（delete）と同じ後始末で未読バッジ・サイドバーを掃除する。
        channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, targetUserId)
                .ifPresent(member -> {
                    member.setLeftAt(OffsetDateTime.now());
                    unreadCounterStore.clear(targetUserId, UnreadCounterStore.channelField(channelId));
                    notificationPublisher.publish(targetUserId, NotificationEvent.channelRemoved(channelId));
                });
    }

    // 招待を無効化する。無効化できるのはチャンネルのアクティブメンバーのみ。
    // 別チャンネルの inviteId は findByIdAndChannelId 不一致で 404。既に無効化済みでも冪等に成功（204）。
    @Transactional
    public void revokeInvite(Long userId, Long channelId, Long inviteId) {
        requireChannelMember(channelId, userId);

        ChannelInvite invite = channelInviteRepository.findByIdAndChannelId(inviteId, channelId)
                .orElseThrow(InviteNotFoundException::new);

        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(OffsetDateTime.now());
        }
    }

    // 呼び出しユーザーが当該チャンネルのアクティブメンバーであることを保証する（非メンバーは 403）。
    private void requireChannelMember(Long channelId, Long userId) {
        channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .orElseThrow(() -> new ChannelForbiddenException(
                        "チャンネルのメンバーではありません: channelId=" + channelId));
    }

    // チャンネルへメンバーとして参加。過去に退出した行があれば left_at をクリアして再参加。
    // 戻り値はメンバーシップが「非アクティブ→アクティブ」へ実際に遷移したか（新規 INSERT / 再参加なら true、
    // 既にアクティブメンバーなら no-op の false）。通知の二重配信を避けるため呼び出し側で利用する。
    private boolean addAsChannelMember(Channel channel, Long userId) {
        ChannelMember existing = channelMemberRepository
                .findByChannelIdAndUserId(channel.getId(), userId)
                .orElse(null);
        if (existing != null) {
            if (existing.getLeftAt() == null) {
                return false; // 既にアクティブメンバー（冪等 no-op）
            }
            existing.setLeftAt(null); // 退出履歴を消して再参加
            return true;
        }
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(userRepository.getReferenceById(userId));
        channelMemberRepository.save(member);
        return true;
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
