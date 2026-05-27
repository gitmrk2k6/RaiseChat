# Flyway シードデータ 学習ノート（チートシート）

PR #12 (A) で実装した「開発用シードデータ `R__seed_dev.sql`」について、**何をしたか / なぜそうしたか / 困った時にどこを見ればいいか** を後から戻ってこれる形にまとめたもの。

---

## 0. 全体像（30 秒で復習）

```text
これまで:
┌─────────────────────────────────────┐
│ フロント (mock)                     │  ← keisuke, haruka… の架空データを直書き
├─────────────────────────────────────┤
│ バックエンド DB                     │  ← 空っぽ
└─────────────────────────────────────┘
   ↑ フロントと DB が別世界 → API を作っても返すデータが無い

今回やった後:
┌─────────────────────────────────────┐
│ フロント (mock)                     │
├─────────────────────────────────────┤
│ バックエンド DB                     │  ← 起動した瞬間に mock と同じデータが入る
└─────────────────────────────────────┘
   ↑ 画面と DB が同じ世界 → C (認証API) 以降の実装が実データで動かせる
```

仕組みの主役は **Flyway**（backend 起動時に SQL を自動実行するツール）。
今回は「テーブル作成 SQL」だけでなく「データ投入 SQL」も Flyway に流させた。

---

## 1. 用語の定義（迷ったら戻る）

| 用語 | 一言 |
| --- | --- |
| **シード（seed）** | 空の DB に「最初に植えておくデータ」。種が植物に育つイメージで、開発を始められる状態にする初期データ |
| **dev プロファイル** | Spring Boot で「開発環境用の設定」と「本番用の設定」を切り替える仕組みの一種 |
| **冪等（idempotent）** | 何回実行しても同じ結果になる性質。シードを何度上書きしても DB が同じ状態に揃う |

---

## 2. 今回作ったファイル一覧

| ファイル | 何が書いてあるか | いつ効くか |
| --- | --- | --- |
| `backend/src/main/resources/db/seed/R__seed_dev.sql` | シードの SQL 本体（INSERT 文の長いリスト） | dev 起動時のみ |
| `backend/src/main/resources/application-dev.yml` | Flyway の探索先に `db/seed/` を追加する設定 | dev 起動時のみ |
| `backend/src/main/resources/application.yml`（1 行追加） | `ignore-migration-patterns: "repeatable:missing"` 安全弁 | 全環境 |

---

## 3. Flyway のファイルには 2 種類ある（V\_\_ と R\_\_）

| 種類 | ファイル名の例 | いつ走る | 中身を編集したら |
| --- | --- | --- | --- |
| **V\_\_** (Versioned) | `V1__init_schema.sql` | **一度きり**（初回のみ） | エラー（編集禁止） |
| **R\_\_** (Repeatable) | `R__seed_dev.sql` | **中身が変わるたび毎回** | OK（再実行される） |

### 使い分けの目安

| やりたいこと | どっち |
| --- | --- |
| テーブルを作る・カラムを足す | V\_\_ |
| ビューや関数を更新する | R\_\_ |
| 開発用シードを育てていく | R\_\_ |
| 本番でも入れたいマスタデータ（管理者ユーザなど） | V\_\_ |

→ **覚え方**: 一度きり = V\_\_、育てるもの = R\_\_。

---

## 4. dev と本番でやり方が違う（今回の核心）

| | dev | 本番 |
| --- | --- | --- |
| DB の中身 | 開発者が書いた架空データ | 実ユーザが作ったデータ |
| 消していい？ | OK（毎回上書き） | **絶対 NG** |
| シードを流す？ | 流す（`R__seed_dev.sql`） | 流さない |
| データの主体 | Flyway が入れる | アプリのサインアップ機能で実ユーザが作る |

→ 本番では基本シードを流さない。Flyway はテーブルの「箱」だけ作って、中身には触らない。
→ 例外的に「最初の管理者アカウント」「国コード一覧」などは V\_\_ で **1 回だけ** INSERT する。

---

## 5. R\_\_ の冪等性（毎回上書きする仕組み）

R\_\_ は中身が変わるたび再実行される。素直に INSERT だけ書くと 2 回目で UNIQUE 制約違反になる。

### 対処：`R__seed_dev.sql` の先頭で全消し

```sql
TRUNCATE TABLE users, workspaces, channels, messages, ...
  RESTART IDENTITY CASCADE;
```

| キーワード | 意味 |
| --- | --- |
| `TRUNCATE TABLE` | 指定テーブルの全行を一括削除（`DELETE` より速い） |
| `RESTART IDENTITY` | `id` の自動採番を 1 に戻す（次の INSERT で id=1 から） |
| `CASCADE` | 外部キーで参照されている関連データも一緒に消す（FK エラー回避） |

### 結果

backend を起動するたび:

1. R\_\_ が走る
2. 先頭で全テーブルをまっさら
3. INSERT で同じデータを入れ直す
4. id も 1 から振り直し → keisuke は毎回 id=1、haruka は毎回 id=2…

→ 何回起動しても同じ状態（冪等）。

**注意**: `TRUNCATE ... CASCADE` は破壊的。**dev 専用 R\_\_** で完結させ、本番設定では絶対読み込ませない。

---

## 6. dev プロファイルで R\_\_ を分離する仕組み

### Spring Profile とは

Spring Boot で「環境ごとに設定を切り替える機能」。

| 起動コマンド | 読まれる設定ファイル |
| --- | --- |
| `./gradlew bootRun` | `application.yml` のみ |
| `SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` | `application.yml` + `application-dev.yml` |

→ ファイル名の末尾 `-dev` が「このファイルは dev 起動時だけ追加で読む」というルール。

### 今回の設定（`application-dev.yml`）

```yaml
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/seed
```

意訳:「dev で起動した時だけ、Flyway は `db/migration/` と `db/seed/` の両方を見る」

| 起動方法 | 流れる SQL |
| --- | --- |
| dev で起動 | `V1__init_schema.sql` + `R__seed_dev.sql` |
| dev 以外で起動 | `V1__init_schema.sql` のみ（シードは流れない） |

→ **本番起動では `R__seed_dev.sql` は読み込まれないので、TRUNCATE が走ることもない**。

---

## 7. もう 1 つの安全弁 `ignore-migration-patterns`

### なぜ必要か

dev で R\_\_ を適用した DB を、後から **dev 以外**で起動すると、Flyway がこう怒る:

```
Detected applied migration not resolved locally: seed dev.
```

→ 「履歴には R\_\_seed_dev を適用したと書いてあるのに、今の locations には R\_\_seed_dev が無い → 異常」と判定。

### 対処：`application.yml` に 1 行

```yaml
spring:
  flyway:
    ignore-migration-patterns: "repeatable:missing"
```

意訳:「R\_\_ ファイルが見当たらなくても怒らないでね」

→ 対象を `repeatable:missing`（R\_\_ の不在）に限定。V\_\_ の不在は依然エラーにするので、本来のスキーマ管理は緩めない。

---

## 8. FK の解決方針（id 直書きを避ける）

シード SQL では各レコードの id を直接書かず、**業務的に意味のあるキー**（`users.user_id`, `workspaces.name` 等）でサブクエリ参照する。

```sql
-- ❌ id を直書き
INSERT INTO workspaces (name, owner_user_id) VALUES ('RaiseTech AI', 1);

-- ✅ business key でサブクエリ
INSERT INTO workspaces (name, owner_user_id)
VALUES ('RaiseTech AI', (SELECT id FROM users WHERE user_id = 'keisuke'));
```

→ 後でデータ並びを変えても壊れない。読み手にも「誰が owner か」が一目でわかる。

---

## 9. スキーマ ↔ フロント mock のマッピング

DB スキーマとフロント mock で表現が違う箇所は、シード側で吸収:

| 項目 | フロント mock | DB スキーマ | 対応 |
| --- | --- | --- | --- |
| `role` | `owner` / `admin` / `member` | `OWNER` / `MEMBER` のみ | `owner` → OWNER, 他 → MEMBER |
| `channel.type` | 小文字 `public` | 大文字 `PUBLIC` | 大文字化 |
| `attachment` 種別 | image / file | mime CHECK で画像系のみ許可 | PDF はスキップ、画像のみ投入 |
| `dm_rooms.user_a_id < user_b_id` | 制約なし | CHECK 制約あり | id 昇順に並べる |
| `created_at` | 相対時刻（JS で生成） | TIMESTAMPTZ | 固定の JST 絶対時刻で書く（再現性） |

---

## 10. 確認のやり方

```bash
# 1. Postgres / Redis を起動
docker compose up -d

# 2. dev プロファイルで起動 → Flyway が R__seed_dev.sql を流す
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# 起動ログに "Migrating schema ... with repeatable migration seed dev" が出ること

# 3. 件数チェック
docker compose exec postgres psql -U raisechat -d raisechat -c \
  "SELECT 'users' tbl, count(*) FROM users UNION ALL
   SELECT 'channels', count(*) FROM channels UNION ALL
   SELECT 'messages', count(*) FROM messages;"
# users=5 / channels=5 / messages=24

# 4. R__ を編集 → 再起動 → 再実行される
#    body をちょっと書き換えてから bootRun し直すと、新しいチェックサムで R__ が再実行される
```

---

## 11. よくあるハマりどころ

### 「Detected applied migration not resolved locally」

→ dev で R\_\_ を入れた DB を、profile 無しで起動して出るエラー。
→ `ignore-migration-patterns: "repeatable:missing"` が `application.yml` に入っているか確認。

### R\_\_ を書き換えても反映されない

→ 中身を **本当に** 変えた？ コメントの空白だけだとチェックサムが変わらず再実行されないことがある。
→ INSERT のデータを 1 文字変える、または DB ごと作り直す（`docker compose down -v`）。

### TRUNCATE が permission denied

→ dev 用 Postgres ユーザに権限が足りない可能性。今回の構成（`raisechat` ユーザが DB オーナー）なら問題なし。

### mime_type の CHECK 制約違反

→ `attachments.mime_type` は `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `video/mp4` のみ許可。PDF などを入れたい時はスキーマ側の CHECK を緩めるか、シード側でスキップする。

---

## 12. 次に進む時の状態

| 状態 |
| --- |
| ✅ V1 スキーマ（B-1） |
| ✅ コア 5 テーブルの JPA Entity / Repository（B-1） |
| ✅ 開発用シードデータ（A）← 今回 |
| ⬜ 認証 API（C）← 次のタスク |
| ⬜ 残り 9 テーブルの Entity / Repository（B-2） |
| ⬜ WebSocket / Redis Pub-Sub（D） |
