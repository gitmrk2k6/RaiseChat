-- =====================================================================
-- V1__init_schema.sql
-- RaiseChat 初期スキーマ
--   - 14 テーブル
--   - インデックス（部分・関数・GIN を含む）
--   - updated_at 自動更新トリガー（5 テーブル）
-- 設計書: docs/database-design.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- 共通トリガー関数: BEFORE UPDATE で updated_at を now() に書き換える
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- テーブル定義
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
  id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         VARCHAR(32)  NOT NULL,
  display_name    VARCHAR(32)  NOT NULL,
  password_hash   VARCHAR(72)  NOT NULL,
  avatar_url      VARCHAR(512),
  status_message  VARCHAR(100) NOT NULL DEFAULT '',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,
  CONSTRAINT users_user_id_format        CHECK (user_id ~ '^[A-Za-z0-9_-]{3,32}$'),
  CONSTRAINT users_display_name_length   CHECK (char_length(display_name) BETWEEN 1 AND 32),
  CONSTRAINT users_status_message_length CHECK (char_length(status_message) <= 100)
);

-- ---------------------------------------------------------------------
-- 2. workspaces
-- ---------------------------------------------------------------------
CREATE TABLE workspaces (
  id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name           VARCHAR(64)  NOT NULL,
  description    VARCHAR(255) NOT NULL DEFAULT '',
  owner_user_id  BIGINT       NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at     TIMESTAMPTZ,
  CONSTRAINT workspaces_name_length CHECK (char_length(name) BETWEEN 1 AND 64),
  CONSTRAINT workspaces_owner_fk
    FOREIGN KEY (owner_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 3. workspace_members
-- ---------------------------------------------------------------------
CREATE TABLE workspace_members (
  id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id  BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  role          VARCHAR(16) NOT NULL,
  joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at       TIMESTAMPTZ,
  CONSTRAINT workspace_members_role_check CHECK (role IN ('OWNER','MEMBER')),
  CONSTRAINT workspace_members_workspace_fk
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT workspace_members_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 4. workspace_invites
-- ---------------------------------------------------------------------
CREATE TABLE workspace_invites (
  id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id        BIGINT      NOT NULL,
  invited_by_user_id  BIGINT      NOT NULL,
  token_hash          VARCHAR(64) NOT NULL,
  expires_at          TIMESTAMPTZ NOT NULL,
  max_uses            INT,
  used_count          INT         NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at          TIMESTAMPTZ,
  CONSTRAINT workspace_invites_workspace_fk
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT workspace_invites_invited_by_fk
    FOREIGN KEY (invited_by_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 5. channels
-- ---------------------------------------------------------------------
CREATE TABLE channels (
  id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id        BIGINT       NOT NULL,
  name                VARCHAR(80)  NOT NULL,
  description         VARCHAR(255) NOT NULL DEFAULT '',
  type                VARCHAR(16)  NOT NULL,
  created_by_user_id  BIGINT       NOT NULL,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at          TIMESTAMPTZ,
  CONSTRAINT channels_name_length CHECK (char_length(name) BETWEEN 1 AND 80),
  CONSTRAINT channels_type_check  CHECK (type IN ('PUBLIC','PRIVATE')),
  CONSTRAINT channels_workspace_fk
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT channels_created_by_fk
    FOREIGN KEY (created_by_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 6. channel_members
-- ---------------------------------------------------------------------
CREATE TABLE channel_members (
  id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  channel_id  BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at     TIMESTAMPTZ,
  CONSTRAINT channel_members_channel_fk
    FOREIGN KEY (channel_id) REFERENCES channels(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT channel_members_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 7. dm_rooms
-- ---------------------------------------------------------------------
CREATE TABLE dm_rooms (
  id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id  BIGINT      NOT NULL,
  user_a_id     BIGINT      NOT NULL,
  user_b_id     BIGINT      NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  CONSTRAINT dm_rooms_user_order CHECK (user_a_id < user_b_id),
  CONSTRAINT dm_rooms_workspace_fk
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT dm_rooms_user_a_fk
    FOREIGN KEY (user_a_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT dm_rooms_user_b_fk
    FOREIGN KEY (user_b_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 8. dm_members
-- ---------------------------------------------------------------------
CREATE TABLE dm_members (
  id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dm_room_id  BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT dm_members_room_fk
    FOREIGN KEY (dm_room_id) REFERENCES dm_rooms(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT dm_members_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 9. messages
-- ---------------------------------------------------------------------
CREATE TABLE messages (
  id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workspace_id       BIGINT      NOT NULL,
  channel_id         BIGINT,
  dm_room_id         BIGINT,
  parent_message_id  BIGINT,
  author_user_id     BIGINT      NOT NULL,
  body               TEXT        NOT NULL,
  body_tsv           tsvector    GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED,
  edited_at          TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at         TIMESTAMPTZ,
  CONSTRAINT messages_body_length CHECK (char_length(body) BETWEEN 1 AND 4000),
  CONSTRAINT messages_channel_or_dm CHECK (
    (channel_id IS NOT NULL) <> (dm_room_id IS NOT NULL)
  ),
  CONSTRAINT messages_workspace_fk
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT messages_channel_fk
    FOREIGN KEY (channel_id) REFERENCES channels(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT messages_dm_room_fk
    FOREIGN KEY (dm_room_id) REFERENCES dm_rooms(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT messages_parent_fk
    FOREIGN KEY (parent_message_id) REFERENCES messages(id)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT messages_author_fk
    FOREIGN KEY (author_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 10. attachments
-- ---------------------------------------------------------------------
CREATE TABLE attachments (
  id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id         BIGINT       NOT NULL,
  uploader_user_id   BIGINT       NOT NULL,
  s3_bucket          VARCHAR(64)  NOT NULL,
  s3_key             VARCHAR(512) NOT NULL,
  mime_type          VARCHAR(64)  NOT NULL,
  size_bytes         BIGINT       NOT NULL,
  original_filename  VARCHAR(255) NOT NULL,
  width              INT,
  height             INT,
  duration_sec       INT,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at         TIMESTAMPTZ,
  CONSTRAINT attachments_mime_check CHECK (
    mime_type IN ('image/jpeg','image/png','image/gif','image/webp','video/mp4')
  ),
  CONSTRAINT attachments_size_check CHECK (size_bytes <= 10485760),
  CONSTRAINT attachments_message_fk
    FOREIGN KEY (message_id) REFERENCES messages(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT attachments_uploader_fk
    FOREIGN KEY (uploader_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 11. reactions
-- ---------------------------------------------------------------------
CREATE TABLE reactions (
  id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id  BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  emoji       VARCHAR(32) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT reactions_message_fk
    FOREIGN KEY (message_id) REFERENCES messages(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT reactions_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 12. mentions
-- ---------------------------------------------------------------------
CREATE TABLE mentions (
  id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id         BIGINT      NOT NULL,
  mentioned_user_id  BIGINT      NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT mentions_message_fk
    FOREIGN KEY (message_id) REFERENCES messages(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT mentions_user_fk
    FOREIGN KEY (mentioned_user_id) REFERENCES users(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 13. read_states
-- ---------------------------------------------------------------------
CREATE TABLE read_states (
  id                    BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id               BIGINT      NOT NULL,
  channel_id            BIGINT,
  dm_room_id            BIGINT,
  last_read_message_id  BIGINT      NOT NULL,
  last_read_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT read_states_channel_or_dm CHECK (
    (channel_id IS NOT NULL) <> (dm_room_id IS NOT NULL)
  ),
  CONSTRAINT read_states_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT read_states_channel_fk
    FOREIGN KEY (channel_id) REFERENCES channels(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT read_states_dm_room_fk
    FOREIGN KEY (dm_room_id) REFERENCES dm_rooms(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT read_states_last_message_fk
    FOREIGN KEY (last_read_message_id) REFERENCES messages(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
);

-- ---------------------------------------------------------------------
-- 14. refresh_tokens
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
  id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  token_hash  VARCHAR(64) NOT NULL,
  issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  CONSTRAINT refresh_tokens_user_fk
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE ON UPDATE CASCADE
);

-- =====================================================================
-- インデックス
-- =====================================================================

-- users
CREATE UNIQUE INDEX idx_users_user_id        ON users (user_id);
CREATE UNIQUE INDEX idx_users_user_id_lower  ON users (LOWER(user_id));

-- refresh_tokens
CREATE UNIQUE INDEX idx_refresh_tokens_token_hash   ON refresh_tokens (token_hash);
CREATE INDEX        idx_refresh_tokens_user_expires ON refresh_tokens (user_id, expires_at);

-- workspace_members
CREATE UNIQUE INDEX idx_workspace_members_workspace_user ON workspace_members (workspace_id, user_id);
CREATE INDEX        idx_workspace_members_user           ON workspace_members (user_id, left_at);

-- workspace_invites
CREATE UNIQUE INDEX idx_workspace_invites_token              ON workspace_invites (token_hash);
CREATE INDEX        idx_workspace_invites_workspace_expires  ON workspace_invites (workspace_id, expires_at);

-- channels
CREATE UNIQUE INDEX idx_channels_workspace_name_lower
  ON channels (workspace_id, LOWER(name))
  WHERE deleted_at IS NULL;
CREATE INDEX idx_channels_workspace
  ON channels (workspace_id)
  WHERE deleted_at IS NULL;

-- channel_members
CREATE UNIQUE INDEX idx_channel_members_channel_user ON channel_members (channel_id, user_id);
CREATE INDEX        idx_channel_members_user         ON channel_members (user_id, left_at);

-- dm_rooms
CREATE UNIQUE INDEX idx_dm_rooms_workspace_users ON dm_rooms (workspace_id, user_a_id, user_b_id);

-- dm_members
CREATE UNIQUE INDEX idx_dm_members_dm_user ON dm_members (dm_room_id, user_id);
CREATE INDEX        idx_dm_members_user    ON dm_members (user_id);

-- messages
CREATE INDEX idx_messages_channel_timeline
  ON messages (channel_id, created_at DESC, id DESC)
  WHERE deleted_at IS NULL AND parent_message_id IS NULL;
CREATE INDEX idx_messages_dm_timeline
  ON messages (dm_room_id, created_at DESC, id DESC)
  WHERE deleted_at IS NULL AND parent_message_id IS NULL;
CREATE INDEX idx_messages_thread
  ON messages (parent_message_id, created_at ASC, id ASC)
  WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_body_tsv
  ON messages USING GIN (body_tsv)
  WHERE deleted_at IS NULL;

-- attachments
CREATE INDEX idx_attachments_message ON attachments (message_id);

-- reactions
CREATE UNIQUE INDEX idx_reactions_message_user_emoji ON reactions (message_id, user_id, emoji);
CREATE INDEX        idx_reactions_message_emoji      ON reactions (message_id, emoji);

-- mentions
CREATE UNIQUE INDEX idx_mentions_message_user   ON mentions (message_id, mentioned_user_id);
CREATE INDEX        idx_mentions_user_timeline  ON mentions (mentioned_user_id, created_at DESC);

-- read_states（XOR を部分インデックスで担保）
CREATE UNIQUE INDEX idx_read_states_user_channel
  ON read_states (user_id, channel_id)
  WHERE channel_id IS NOT NULL;
CREATE UNIQUE INDEX idx_read_states_user_dm
  ON read_states (user_id, dm_room_id)
  WHERE dm_room_id IS NOT NULL;

-- =====================================================================
-- updated_at 自動更新トリガー（updated_at 列を持つ 5 テーブル）
-- =====================================================================

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_workspaces_updated_at
  BEFORE UPDATE ON workspaces
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_channels_updated_at
  BEFORE UPDATE ON channels
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_messages_updated_at
  BEFORE UPDATE ON messages
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_read_states_updated_at
  BEFORE UPDATE ON read_states
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
