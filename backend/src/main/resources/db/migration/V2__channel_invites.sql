-- =====================================================================
-- V2__channel_invites.sql
-- F-15 チャンネル招待: channel_invites テーブルを追加
--   - workspace_invites（V1）をミラーした構造
--   - チャンネル単位の招待リンク（発行はチャンネルメンバー、受諾は WS メンバー）
-- 設計書: docs/api-design.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- channel_invites
-- ---------------------------------------------------------------------
CREATE TABLE channel_invites (
  id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  channel_id          BIGINT      NOT NULL,
  invited_by_user_id  BIGINT      NOT NULL,
  token_hash          VARCHAR(64) NOT NULL,
  expires_at          TIMESTAMPTZ NOT NULL,
  max_uses            INT,
  used_count          INT         NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at          TIMESTAMPTZ,
  CONSTRAINT channel_invites_channel_fk
    FOREIGN KEY (channel_id) REFERENCES channels(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT channel_invites_invited_by_fk
    FOREIGN KEY (invited_by_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- token_hash は受諾時の検索キー。グローバルに一意。
CREATE UNIQUE INDEX idx_channel_invites_token             ON channel_invites (token_hash);
CREATE INDEX        idx_channel_invites_channel_expires   ON channel_invites (channel_id, expires_at);
