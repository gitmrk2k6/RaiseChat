package com.raisechat.workspace;

import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelMember;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.channel.ChannelType;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.dto.CreateInviteRequest;
import com.raisechat.workspace.dto.CreateWorkspaceRequest;
import com.raisechat.workspace.dto.InviteResponse;
import com.raisechat.workspace.dto.WorkspaceDetailResponse;
import com.raisechat.workspace.dto.WorkspaceListResponse;
import com.raisechat.workspace.dto.WorkspaceResponse;
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
public class WorkspaceService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    // ws 作成時に自動生成されるデフォルトチャンネル名。招待受諾時はここへ自動参加させる。
    private static final String GENERAL_CHANNEL_NAME = "general";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final InviteTokenService inviteTokenService;

    // 招待 URL の組み立てに使うフロントエンドのベース URL。
    @Value("${app.invite.base-url:http://localhost:3000/invite}")
    private String inviteBaseUrl;

    // created_at / updated_at は DB トリガーで設定するため、INSERT 後に refresh で読み戻す。
    @PersistenceContext
    private EntityManager entityManager;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInviteRepository workspaceInviteRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            InviteTokenService inviteTokenService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInviteRepository = workspaceInviteRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.inviteTokenService = inviteTokenService;
    }

    @Transactional
    public WorkspaceResponse create(Long userId, CreateWorkspaceRequest req) {
        User owner = userRepository.getReferenceById(userId);

        Workspace ws = new Workspace();
        ws.setName(req.name());
        ws.setDescription(req.description() == null ? "" : req.description());
        ws.setOwner(owner);
        workspaceRepository.saveAndFlush(ws);

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(ws);
        member.setUser(owner);
        member.setRole(WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member);

        // ws 作成時に general チャンネル（PUBLIC、作成者をメンバーに登録）を自動作成する。
        // F-05 メッセージ API が「最低 1 つチャンネルが存在する」前提のため。
        Channel general = new Channel();
        general.setWorkspace(ws);
        general.setName("general");
        general.setDescription("");
        general.setType(ChannelType.PUBLIC);
        general.setCreatedBy(owner);
        channelRepository.saveAndFlush(general);

        ChannelMember generalMember = new ChannelMember();
        generalMember.setChannel(general);
        generalMember.setUser(owner);
        channelMemberRepository.saveAndFlush(generalMember);

        entityManager.refresh(ws);
        return WorkspaceResponse.from(ws);
    }

    @Transactional(readOnly = true)
    public WorkspaceListResponse listMine(Long userId, String cursor, Integer limit) {
        int effectiveLimit = clampLimit(limit);
        Long cursorId = parseCursor(cursor);

        Pageable pageable = PageRequest.ofSize(effectiveLimit + 1);
        List<Workspace> rows = workspaceRepository.findAccessibleByUserIdAfterCursor(userId, cursorId, pageable);

        boolean hasMore = rows.size() > effectiveLimit;
        List<Workspace> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<WorkspaceResponse> items = page.stream().map(WorkspaceResponse::from).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new WorkspaceListResponse(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public WorkspaceDetailResponse getDetail(Long userId, Long workspaceId) {
        Workspace ws = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));

        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceForbiddenException(workspaceId, userId));

        List<WorkspaceMember> members = workspaceMemberRepository.findActiveByWorkspaceIdWithUser(workspaceId);

        return WorkspaceDetailResponse.from(ws, members);
    }

    // ---------- 招待 (F-15) ----------

    // 招待リンクを発行する。OWNER のみ。平文トークンはこのレスポンスでのみ返す。
    @Transactional
    public InviteResponse createInvite(Long userId, Long workspaceId, CreateInviteRequest req) {
        Workspace ws = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        requireWorkspaceOwner(workspaceId, userId);

        String rawToken = inviteTokenService.generateRawToken();

        WorkspaceInvite invite = new WorkspaceInvite();
        invite.setWorkspace(ws);
        invite.setInvitedBy(userRepository.getReferenceById(userId));
        invite.setTokenHash(inviteTokenService.hash(rawToken));
        invite.setExpiresAt(OffsetDateTime.now().plusHours(req.resolveExpiresInHours()));
        invite.setMaxUses(req.maxUses());
        workspaceInviteRepository.saveAndFlush(invite);

        // created_at は DB デフォルトで設定されるため refresh で読み戻す。
        entityManager.refresh(invite);

        return InviteResponse.from(invite, rawToken, inviteBaseUrl + "/" + rawToken);
    }

    // 招待を受諾し、呼び出しユーザーをワークスペース（＋ general チャンネル）のメンバーにする。
    // 既にメンバーの場合は冪等に 200（used_count は増やさない）。
    @Transactional
    public WorkspaceResponse acceptInvite(Long userId, String rawToken) {
        WorkspaceInvite invite = workspaceInviteRepository.findByTokenHash(inviteTokenService.hash(rawToken))
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

        Workspace ws = invite.getWorkspace();
        if (ws.getDeletedAt() != null) {
            // ワークスペースが削除済みなら招待は実質無効。トークン総当たり対策で 404 に倒す。
            throw new InviteNotFoundException();
        }

        // 既にアクティブメンバーなら冪等に成功（使用回数も消費しない）。
        if (workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(ws.getId(), userId).isPresent()) {
            return WorkspaceResponse.from(ws);
        }

        addAsMember(ws, userId);
        joinGeneralChannel(ws.getId(), userId);

        // read-modify-write。@Transactional(READ_COMMITTED) 下のため高並行では max_uses を
        // わずかに超過しうるが、招待ユースケースでは許容。厳密化が必要なら @Version 楽観ロック、
        // または条件付き UPDATE (used_count = used_count + 1 WHERE used_count < max_uses) に切替可能。
        invite.setUsedCount(invite.getUsedCount() + 1);

        return WorkspaceResponse.from(ws);
    }

    // 招待を無効化する。OWNER のみ。既に無効化済みでも冪等に成功（204）。
    @Transactional
    public void revokeInvite(Long userId, Long workspaceId, Long inviteId) {
        requireWorkspaceOwner(workspaceId, userId);

        WorkspaceInvite invite = workspaceInviteRepository.findByIdAndWorkspaceId(inviteId, workspaceId)
                .orElseThrow(InviteNotFoundException::new);

        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(OffsetDateTime.now());
        }
    }

    // 呼び出しユーザーがワークスペースの OWNER であることを保証する。
    // 非メンバー・非 OWNER ともに 403（WorkspaceForbiddenException）。
    private void requireWorkspaceOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceForbiddenException(workspaceId, userId));
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceForbiddenException(workspaceId, userId);
        }
    }

    // role=MEMBER として参加。過去に退出した行があれば left_at をクリアして再参加。
    private void addAsMember(Workspace ws, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(ws.getId(), userId)
                .ifPresentOrElse(existing -> { /* 既にアクティブ: 呼び出し側で除外済み */ }, () -> {
                    WorkspaceMember member = new WorkspaceMember();
                    member.setWorkspace(ws);
                    member.setUser(userRepository.getReferenceById(userId));
                    member.setRole(WorkspaceRole.MEMBER);
                    workspaceMemberRepository.save(member);
                });
    }

    // general チャンネル（PUBLIC）へ自動参加。存在しない ws ではスキップ。
    private void joinGeneralChannel(Long workspaceId, Long userId) {
        channelRepository.findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, GENERAL_CHANNEL_NAME)
                .ifPresent(general -> channelMemberRepository.findByChannelIdAndUserId(general.getId(), userId)
                        .ifPresentOrElse(existing -> existing.setLeftAt(null), () -> {
                            ChannelMember member = new ChannelMember();
                            member.setChannel(general);
                            member.setUser(userRepository.getReferenceById(userId));
                            channelMemberRepository.save(member);
                        }));
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
