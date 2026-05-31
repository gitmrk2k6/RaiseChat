package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.SearchResultResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// F-13 メッセージ検索。パスは /api/workspaces/{wsId}/search に揃える（ワークスペース配下のリソース）。
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/workspaces/{wsId}/search")
    public SearchResultResponse search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return searchService.search(principal.id(), wsId, q, cursor, limit);
    }
}
