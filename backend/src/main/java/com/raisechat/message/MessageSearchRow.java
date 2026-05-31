package com.raisechat.message;

import java.time.Instant;

// F-13 全文検索のネイティブクエリ結果を受ける Spring Data プロジェクション。
// native query では JOIN FETCH が使えないため、author / channel を JOIN したカラムを
// この射影で一括取得し、メッセージごとの追加 SELECT（N+1）を避ける。
// timestamptz カラムは JDBC が Instant で返すため、ここでも Instant で受け、
// SearchService 側で OffsetDateTime（UTC）へ変換する。
public interface MessageSearchRow {
    Long getId();
    Long getChannelId();
    String getChannelName();
    Long getDmRoomId();
    Long getParentMessageId();
    Long getAuthorId();
    String getAuthorUserId();
    String getAuthorDisplayName();
    String getBody();
    Instant getEditedAt();
    Instant getCreatedAt();
}
