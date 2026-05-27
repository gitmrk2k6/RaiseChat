-- =====================================================================
-- R__seed_dev.sql
-- 開発用シードデータ（frontend/lib/mock/ と同期）
--
-- - dev プロファイル時のみ実行（application-dev.yml で db/seed を locations に追加）
-- - R__ なのでチェックサムが変わるたびに自動再実行される
-- - 先頭で TRUNCATE ... RESTART IDENTITY CASCADE するため、開発 DB 以外で
--   実行すると既存データが失われる点に注意
-- - password_hash は全員共通の bcrypt ハッシュ（平文 "password"）
-- - FK は id 直書きを避け、business key（user_id / name 等）の
--   サブクエリで解決する
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. クリーンアップ（FK 連鎖を CASCADE で一括、id は 1 から振り直し）
-- ---------------------------------------------------------------------
TRUNCATE TABLE
  users, workspaces, workspace_members, workspace_invites,
  channels, channel_members,
  dm_rooms, dm_members,
  messages, attachments, reactions, mentions,
  read_states, refresh_tokens
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------
-- 1. users（5名）
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, display_name, password_hash, status_message) VALUES
  ('keisuke', 'Keisuke Konishi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '🎧 集中モード'),
  ('haruka',  'Haruka Sato',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '出社中'),
  ('ryo',     'Ryo Yamamoto',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'リモートワーク'),
  ('mika',    'Mika Tanaka',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', ''),
  ('kenta',   'Kenta Suzuki',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '🍵 休憩中');

-- ---------------------------------------------------------------------
-- 2. workspaces（2件、owner はいずれも keisuke）
-- ---------------------------------------------------------------------
INSERT INTO workspaces (name, description, owner_user_id) VALUES
  ('RaiseTech AI', '', (SELECT id FROM users WHERE user_id = 'keisuke')),
  ('Side Project', '', (SELECT id FROM users WHERE user_id = 'keisuke'));

-- ---------------------------------------------------------------------
-- 3. workspace_members
--   ws-1 (RaiseTech AI): 5名（keisuke=OWNER, 他=MEMBER）
--   ws-2 (Side Project): keisuke のみ（OWNER）
-- ---------------------------------------------------------------------
INSERT INTO workspace_members (workspace_id, user_id, role) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='keisuke'), 'OWNER'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='haruka'),  'MEMBER'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='ryo'),     'MEMBER'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='mika'),    'MEMBER'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='kenta'),   'MEMBER'),
  ((SELECT id FROM workspaces WHERE name='Side Project'),
   (SELECT id FROM users WHERE user_id='keisuke'), 'OWNER');

-- ---------------------------------------------------------------------
-- 4. channels（5件、すべて ws-1。created_by=keisuke）
-- ---------------------------------------------------------------------
INSERT INTO channels (workspace_id, name, description, type, created_by_user_id) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   'general', '全員参加の総合チャンネルです 👋', 'PUBLIC',
   (SELECT id FROM users WHERE user_id='keisuke')),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   'random', '雑談やランチの話など、お気軽にどうぞ ☕️', 'PUBLIC',
   (SELECT id FROM users WHERE user_id='keisuke')),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   'dev-backend', 'Spring Boot / Java / DB 関連の話題', 'PUBLIC',
   (SELECT id FROM users WHERE user_id='keisuke')),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   'secret-pj', '新規プロジェクト用 🔒', 'PRIVATE',
   (SELECT id FROM users WHERE user_id='keisuke')),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   'design', 'Figma / UI / UX 関連', 'PUBLIC',
   (SELECT id FROM users WHERE user_id='keisuke'));

-- ---------------------------------------------------------------------
-- 5. channel_members
-- ---------------------------------------------------------------------
INSERT INTO channel_members (channel_id, user_id)
SELECT c.id, u.id
FROM channels c, users u
WHERE c.name = 'general'
  AND u.user_id IN ('keisuke','haruka','ryo','mika','kenta');

INSERT INTO channel_members (channel_id, user_id)
SELECT c.id, u.id
FROM channels c, users u
WHERE c.name = 'random'
  AND u.user_id IN ('keisuke','haruka','ryo','mika','kenta');

INSERT INTO channel_members (channel_id, user_id)
SELECT c.id, u.id
FROM channels c, users u
WHERE c.name = 'dev-backend'
  AND u.user_id IN ('keisuke','haruka','ryo');

INSERT INTO channel_members (channel_id, user_id)
SELECT c.id, u.id
FROM channels c, users u
WHERE c.name = 'secret-pj'
  AND u.user_id IN ('keisuke','ryo');

INSERT INTO channel_members (channel_id, user_id)
SELECT c.id, u.id
FROM channels c, users u
WHERE c.name = 'design'
  AND u.user_id IN ('keisuke','mika');

-- ---------------------------------------------------------------------
-- 6. dm_rooms（user_a_id < user_b_id 制約: keisuke=1, haruka=2, ryo=3）
-- ---------------------------------------------------------------------
INSERT INTO dm_rooms (workspace_id, user_a_id, user_b_id) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='keisuke'),
   (SELECT id FROM users WHERE user_id='haruka')),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM users WHERE user_id='keisuke'),
   (SELECT id FROM users WHERE user_id='ryo'));

-- ---------------------------------------------------------------------
-- 7. dm_members（各 DM の 2名）
-- ---------------------------------------------------------------------
INSERT INTO dm_members (dm_room_id, user_id)
SELECT d.id, u.id
FROM dm_rooms d, users u
WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
  AND d.user_b_id = (SELECT id FROM users WHERE user_id='haruka')
  AND u.user_id IN ('keisuke','haruka');

INSERT INTO dm_members (dm_room_id, user_id)
SELECT d.id, u.id
FROM dm_rooms d, users u
WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
  AND d.user_b_id = (SELECT id FROM users WHERE user_id='ryo')
  AND u.user_id IN ('keisuke','ryo');

-- ---------------------------------------------------------------------
-- 8. messages（トップレベル：channel 13件 + DM 5件）
--   時刻は固定の JST 絶対値。フロント mock は相対だが、シードは再現性優先。
-- ---------------------------------------------------------------------

-- === ch-general（5件） ===
INSERT INTO messages (workspace_id, channel_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='general'),
   (SELECT id FROM users     WHERE user_id='ryo'),
   'おはようございます！今週もよろしくお願いします 🙌',
   '2026-05-26 09:12:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='general'),
   (SELECT id FROM users     WHERE user_id='haruka'),
   '今日の 14:00 から **スプリントレビュー** です。資料はこちら → [Notion](https://example.com)',
   '2026-05-26 10:30:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='general'),
   (SELECT id FROM users     WHERE user_id='keisuke'),
   '了解です！議事録の準備しておきます ✍️',
   '2026-05-26 10:35:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='general'),
   (SELECT id FROM users     WHERE user_id='mika'),
   '@keisuke デザインのレビューもお願いできますか？',
   '2026-05-26 22:45:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='general'),
   (SELECT id FROM users     WHERE user_id='keisuke'),
   '了解です！夕方までに見ます 👍',
   '2026-05-26 23:00:00+09');

-- m-g-5 だけ edited_at を更新
UPDATE messages SET edited_at = '2026-05-27 00:10:00+09'
WHERE body = '了解です！夕方までに見ます 👍'
  AND author_user_id = (SELECT id FROM users WHERE user_id='keisuke')
  AND channel_id     = (SELECT id FROM channels WHERE name='general');

-- === ch-random（3件） ===
INSERT INTO messages (workspace_id, channel_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='random'),
   (SELECT id FROM users     WHERE user_id='kenta'),
   '近所に新しくできたカフェ、めちゃくちゃ良かったです ☕️
写真貼っておきます',
   '2026-05-26 19:40:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='random'),
   (SELECT id FROM users     WHERE user_id='haruka'),
   '@keisuke ランチ行きませんか？🍜',
   '2026-05-27 00:30:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='random'),
   (SELECT id FROM users     WHERE user_id='ryo'),
   '私も行きたい！',
   '2026-05-27 00:40:00+09');

-- === ch-dev-backend（3件） ===
INSERT INTO messages (workspace_id, channel_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='dev-backend'),
   (SELECT id FROM users     WHERE user_id='ryo'),
   E'Spring Boot 3.x の WebSocket 設定、こんな感じで行こうと思います：\n```java\n@Configuration\n@EnableWebSocketMessageBroker\npublic class WebSocketConfig implements WebSocketMessageBrokerConfigurer {\n    @Override\n    public void configureMessageBroker(MessageBrokerRegistry config) {\n        config.enableSimpleBroker("/topic");\n        config.setApplicationDestinationPrefixes("/app");\n    }\n}\n```',
   '2026-05-26 17:00:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='dev-backend'),
   (SELECT id FROM users     WHERE user_id='keisuke'),
   '良さそうですね！後で Redis Pub/Sub と組み合わせる方針も検討しましょう',
   '2026-05-26 17:30:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='dev-backend'),
   (SELECT id FROM users     WHERE user_id='haruka'),
   'DB スキーマも今日中に Draft 出します',
   '2026-05-26 21:00:00+09');

-- === ch-secret-pj（1件） ===
INSERT INTO messages (workspace_id, channel_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='secret-pj'),
   (SELECT id FROM users     WHERE user_id='ryo'),
   '新規プロジェクトの方針、ドキュメントにまとめました 📄',
   '2026-05-26 22:00:00+09');

-- === ch-design（1件） ===
INSERT INTO messages (workspace_id, channel_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT id FROM channels  WHERE name='design'),
   (SELECT id FROM users     WHERE user_id='mika'),
   '新しいロゴ案、3パターン作りました。意見聞かせてください 🎨',
   '2026-05-26 19:00:00+09');

-- === DM 1: keisuke ⇔ haruka（3件） ===
INSERT INTO messages (workspace_id, dm_room_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT d.id FROM dm_rooms d
      WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
        AND d.user_b_id = (SELECT id FROM users WHERE user_id='haruka')),
   (SELECT id FROM users WHERE user_id='haruka'),
   'お疲れさまです！明日のレビュー、よろしくお願いします',
   '2026-05-26 23:00:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT d.id FROM dm_rooms d
      WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
        AND d.user_b_id = (SELECT id FROM users WHERE user_id='haruka')),
   (SELECT id FROM users WHERE user_id='keisuke'),
   '承知しました 👍 資料事前に共有しますね',
   '2026-05-26 23:10:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT d.id FROM dm_rooms d
      WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
        AND d.user_b_id = (SELECT id FROM users WHERE user_id='haruka')),
   (SELECT id FROM users WHERE user_id='haruka'),
   E'助かります！\n明日のレビュー、よろしくお願いします！',
   '2026-05-27 00:30:00+09');

-- === DM 2: keisuke ⇔ ryo（2件） ===
INSERT INTO messages (workspace_id, dm_room_id, author_user_id, body, created_at) VALUES
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT d.id FROM dm_rooms d
      WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
        AND d.user_b_id = (SELECT id FROM users WHERE user_id='ryo')),
   (SELECT id FROM users WHERE user_id='keisuke'),
   'PR #5 出しました。お時間あるときレビューお願いします 🙏',
   '2026-05-26 21:00:00+09'),
  ((SELECT id FROM workspaces WHERE name='RaiseTech AI'),
   (SELECT d.id FROM dm_rooms d
      WHERE d.user_a_id = (SELECT id FROM users WHERE user_id='keisuke')
        AND d.user_b_id = (SELECT id FROM users WHERE user_id='ryo')),
   (SELECT id FROM users WHERE user_id='ryo'),
   'OK、PRレビューしておきます 👍',
   '2026-05-26 21:30:00+09');

-- ---------------------------------------------------------------------
-- 9. messages（スレッド返信 6件、parent_message_id は親 body で解決）
-- ---------------------------------------------------------------------

-- m-g-2 への返信 2件
INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='general'),
  m.id,
  (SELECT id FROM users WHERE user_id='keisuke'),
  '資料、確認しました！1点質問あります',
  '2026-05-26 11:00:00+09'
FROM messages m
WHERE m.body LIKE '今日の 14:00 から %';

INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='general'),
  m.id,
  (SELECT id FROM users WHERE user_id='ryo'),
  'どうぞ！',
  '2026-05-26 11:02:00+09'
FROM messages m
WHERE m.body LIKE '今日の 14:00 から %';

-- m-r-1 への返信 1件
INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='random'),
  m.id,
  (SELECT id FROM users WHERE user_id='haruka'),
  '場所教えてください！今度行きたい！',
  '2026-05-26 21:00:00+09'
FROM messages m
WHERE m.body LIKE '近所に新しくできたカフェ%';

-- m-d-1 への返信 3件
INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='dev-backend'),
  m.id,
  (SELECT id FROM users WHERE user_id='keisuke'),
  '`enableSimpleBroker` ではなく STOMP broker relay にしなくて大丈夫ですか？',
  '2026-05-26 17:10:00+09'
FROM messages m
WHERE m.body LIKE 'Spring Boot 3.x の WebSocket 設定%';

INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='dev-backend'),
  m.id,
  (SELECT id FROM users WHERE user_id='ryo'),
  'MVP段階では simple broker で十分かと。スケール時に切り替え検討しましょう',
  '2026-05-26 17:15:00+09'
FROM messages m
WHERE m.body LIKE 'Spring Boot 3.x の WebSocket 設定%';

INSERT INTO messages (workspace_id, channel_id, parent_message_id, author_user_id, body, created_at)
SELECT
  (SELECT id FROM workspaces WHERE name='RaiseTech AI'),
  (SELECT id FROM channels  WHERE name='dev-backend'),
  m.id,
  (SELECT id FROM users WHERE user_id='haruka'),
  '了解です！',
  '2026-05-26 17:20:00+09'
FROM messages m
WHERE m.body LIKE 'Spring Boot 3.x の WebSocket 設定%';

-- ---------------------------------------------------------------------
-- 10. attachments（cafe.jpg のみ。PDF は mime CHECK 制約で投入不可）
-- ---------------------------------------------------------------------
INSERT INTO attachments
  (message_id, uploader_user_id, s3_bucket, s3_key, mime_type, size_bytes, original_filename, width, height)
SELECT
  m.id,
  (SELECT id FROM users WHERE user_id='kenta'),
  'raisechat-dev',
  'attachments/seed/cafe.jpg',
  'image/jpeg',
  234567,
  'cafe.jpg',
  600,
  400
FROM messages m
WHERE m.body LIKE '近所に新しくできたカフェ%';

-- ---------------------------------------------------------------------
-- 11. reactions
-- ---------------------------------------------------------------------
-- m-g-1: 👋 by keisuke, haruka, mika
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '👋'
FROM messages m, users u
WHERE m.body = 'おはようございます！今週もよろしくお願いします 🙌'
  AND u.user_id IN ('keisuke','haruka','mika');

-- m-g-2: 👀 by keisuke
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '👀'
FROM messages m, users u
WHERE m.body LIKE '今日の 14:00 から %'
  AND u.user_id = 'keisuke';

-- m-g-5: 🙏 by mika
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '🙏'
FROM messages m, users u
WHERE m.body = '了解です！夕方までに見ます 👍'
  AND m.channel_id = (SELECT id FROM channels WHERE name='general')
  AND u.user_id = 'mika';

-- m-r-1: ☕️ by haruka, ryo
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '☕️'
FROM messages m, users u
WHERE m.body LIKE '近所に新しくできたカフェ%'
  AND u.user_id IN ('haruka','ryo');

-- m-r-1: 😍 by mika
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '😍'
FROM messages m, users u
WHERE m.body LIKE '近所に新しくできたカフェ%'
  AND u.user_id = 'mika';

-- m-d-1: 🚀 by keisuke, haruka
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '🚀'
FROM messages m, users u
WHERE m.body LIKE 'Spring Boot 3.x の WebSocket 設定%'
  AND u.user_id IN ('keisuke','haruka');

-- m-d-3: 👍 by keisuke, ryo
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '👍'
FROM messages m, users u
WHERE m.body = 'DB スキーマも今日中に Draft 出します'
  AND u.user_id IN ('keisuke','ryo');

-- m-de-1: 🎨 by keisuke
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '🎨'
FROM messages m, users u
WHERE m.body = '新しいロゴ案、3パターン作りました。意見聞かせてください 🎨'
  AND u.user_id = 'keisuke';

-- m-de-1: ❤️ by keisuke
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '❤️'
FROM messages m, users u
WHERE m.body = '新しいロゴ案、3パターン作りました。意見聞かせてください 🎨'
  AND u.user_id = 'keisuke';

-- m-d-1-r2 (ryo の返信): 👍 by keisuke
INSERT INTO reactions (message_id, user_id, emoji)
SELECT m.id, u.id, '👍'
FROM messages m, users u
WHERE m.body = 'MVP段階では simple broker で十分かと。スケール時に切り替え検討しましょう'
  AND u.user_id = 'keisuke';

-- ---------------------------------------------------------------------
-- 12. mentions
-- ---------------------------------------------------------------------
-- m-g-4: mika が keisuke にメンション
INSERT INTO mentions (message_id, mentioned_user_id)
SELECT m.id, (SELECT id FROM users WHERE user_id='keisuke')
FROM messages m
WHERE m.body = '@keisuke デザインのレビューもお願いできますか？';

-- m-r-2: haruka が keisuke にメンション
INSERT INTO mentions (message_id, mentioned_user_id)
SELECT m.id, (SELECT id FROM users WHERE user_id='keisuke')
FROM messages m
WHERE m.body = '@keisuke ランチ行きませんか？🍜';

COMMIT;
