package com.raisechat.dm;

import com.raisechat.dm.dto.CreateDmRoomRequest;
import com.raisechat.dm.dto.DmRoomCreationResult;
import com.raisechat.dm.dto.DmRoomListResponse;
import com.raisechat.dm.dto.DmRoomResponse;
import com.raisechat.dm.exception.DmForbiddenException;
import com.raisechat.dm.exception.DmNotFoundException;
import com.raisechat.dm.exception.DmValidationException;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.Workspace;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRepository;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DmService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DmRoomRepository dmRoomRepository;
    private final DmMemberRepository dmMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DmService(
            DmRoomRepository dmRoomRepository,
            DmMemberRepository dmMemberRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        this.dmRoomRepository = dmRoomRepository;
        this.dmMemberRepository = dmMemberRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional
    public DmRoomCreationResult createOrGetRoom(Long userId, Long workspaceId, CreateDmRoomRequest req) {
        Long partnerUserId = req.partnerUserId();

        if (userId.equals(partnerUserId)) {
            throw new DmValidationException("自分自身を DM 相手に指定できません");
        }

        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));

        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new DmForbiddenException(
                        "ワークスペースのメンバーではありません: workspaceId=" + workspaceId));

        userRepository.findById(partnerUserId)
                .orElseThrow(() -> DmNotFoundException.partner(partnerUserId));

        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, partnerUserId)
                .orElseThrow(() -> new DmForbiddenException(
                        "相手は同一ワークスペースのメンバーではありません: partnerUserId=" + partnerUserId));

        Long smallerId = Math.min(userId, partnerUserId);
        Long largerId = Math.max(userId, partnerUserId);

        return dmRoomRepository
                .findByWorkspaceIdAndUserAIdAndUserBId(workspaceId, smallerId, largerId)
                .map(existing -> {
                    DmRoom withUsers = dmRoomRepository.findActiveByIdWithUsers(existing.getId())
                            .orElse(existing);
                    return new DmRoomCreationResult(DmRoomResponse.from(withUsers), false);
                })
                .orElseGet(() -> {
                    User userA = userRepository.getReferenceById(smallerId);
                    User userB = userRepository.getReferenceById(largerId);

                    DmRoom room = new DmRoom();
                    room.setWorkspace(workspace);
                    room.setUserA(userA);
                    room.setUserB(userB);
                    dmRoomRepository.saveAndFlush(room);
                    entityManager.refresh(room);

                    DmMember memberA = new DmMember();
                    memberA.setDmRoom(room);
                    memberA.setUser(userA);
                    DmMember memberB = new DmMember();
                    memberB.setDmRoom(room);
                    memberB.setUser(userB);
                    dmMemberRepository.saveAll(List.of(memberA, memberB));

                    DmRoom loaded = dmRoomRepository.findActiveByIdWithUsers(room.getId())
                            .orElse(room);
                    return new DmRoomCreationResult(DmRoomResponse.from(loaded), true);
                });
    }

    @Transactional(readOnly = true)
    public DmRoomListResponse listMyRooms(Long userId, Long workspaceId, Long cursorId, Integer limit) {
        workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));

        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new DmForbiddenException(
                        "ワークスペースのメンバーではありません: workspaceId=" + workspaceId));

        int effectiveLimit = clampLimit(limit);
        long effectiveCursor = cursorId != null ? cursorId : 0L;

        List<DmRoom> rows = dmRoomRepository.findUserRoomsBefore(
                workspaceId, userId, effectiveCursor, PageRequest.ofSize(effectiveLimit + 1));

        boolean hasMore = rows.size() > effectiveLimit;
        List<DmRoom> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<DmRoomResponse> items = page.stream().map(DmRoomResponse::from).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new DmRoomListResponse(items, nextCursor, hasMore);
    }

    public void requireDmMember(Long dmRoomId, Long userId) {
        if (!dmMemberRepository.existsByDmRoomIdAndUserId(dmRoomId, userId)) {
            throw new DmForbiddenException("DM ルームの参加者ではありません: dmRoomId=" + dmRoomId);
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
