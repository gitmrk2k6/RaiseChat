package com.raisechat.workspace;

import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.dto.CreateWorkspaceRequest;
import com.raisechat.workspace.dto.WorkspaceDetailResponse;
import com.raisechat.workspace.dto.WorkspaceListResponse;
import com.raisechat.workspace.dto.WorkspaceResponse;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkspaceService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    // created_at / updated_at は DB トリガーで設定するため、INSERT 後に refresh で読み戻す。
    @PersistenceContext
    private EntityManager entityManager;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
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
