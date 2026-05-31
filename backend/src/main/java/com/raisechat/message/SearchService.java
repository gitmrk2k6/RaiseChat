package com.raisechat.message;

import com.raisechat.message.dto.SearchResultItem;
import com.raisechat.message.dto.SearchResultResponse;
import com.raisechat.message.exception.SearchValidationException;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRepository;
import com.raisechat.workspace.exception.WorkspaceForbiddenException;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// F-13 メッセージ全文検索。閲覧可能なチャンネル / DM のメッセージ本文を PostgreSQL の
// 全文検索（body_tsv + websearch_to_tsquery）で横断検索する。アクセス制御は
// MessageRepository.searchInWorkspace のサブクエリ側で表現する。
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final MessageRepository messageRepository;

    public SearchService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            MessageRepository messageRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public SearchResultResponse search(Long userId, Long workspaceId, String q, Long cursorId, Integer limit) {
        // ワークスペースの存在（404）→ メンバーシップ（403）の順に検証する（getDetail と同じ方針）。
        workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceForbiddenException(workspaceId, userId));

        String query = q == null ? "" : q.strip();
        if (query.isEmpty()) {
            throw new SearchValidationException("検索キーワード（q）を指定してください");
        }

        int effectiveLimit = clampLimit(limit);
        long effectiveCursor = cursorId != null ? cursorId : 0L;

        List<MessageSearchRow> rows = messageRepository.searchInWorkspace(
                workspaceId, userId, query, effectiveCursor, PageRequest.ofSize(effectiveLimit + 1));

        boolean hasMore = rows.size() > effectiveLimit;
        List<MessageSearchRow> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<SearchResultItem> items = page.stream().map(SearchService::toItem).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new SearchResultResponse(items, nextCursor, hasMore);
    }

    private static SearchResultItem toItem(MessageSearchRow r) {
        return new SearchResultItem(
                r.getId(),
                r.getChannelId(),
                r.getChannelName(),
                r.getDmRoomId(),
                r.getParentMessageId(),
                r.getAuthorId(),
                r.getAuthorUserId(),
                r.getAuthorDisplayName(),
                r.getBody(),
                toOffset(r.getEditedAt()),
                toOffset(r.getCreatedAt())
        );
    }

    // JDBC が返す Instant（UTC の時点）を OffsetDateTime(UTC) として表現する。
    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
