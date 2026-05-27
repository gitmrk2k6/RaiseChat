# バックエンド初期化 学習ノート（チートシート）

PR #6 で実装した「docker-compose + Spring Boot 3.5 + Flyway V1」のバックエンド土台について、**何が起きていたか / なぜそうしたか / 困った時にどこを見ればいいか** を後から戻ってこれる形にまとめたもの。

---

## 0. 全体像（30 秒で復習）

```text
ブラウザ
   ↕
Next.js (port 3000)        ← フロントエンド
   ↕
Spring Boot (port 8080)    ← 今回作った「バックエンドの土台」
   ↕
PostgreSQL (port 5432)     ← Docker で起動
Redis      (port 6379)     ← Docker で起動
```

| ソフトウェア | ひと言 |
| --- | --- |
| PostgreSQL | データベース本体。ユーザー・メッセージなどを保存 |
| Redis | キャッシュ + リアルタイム配信用の高速 DB |
| Spring Boot | Java の Web フレームワーク。API サーバー本体 |
| Flyway | DB スキーマのバージョン管理ツール（Git の DB 版） |
| Docker | コンテナ実行環境。PostgreSQL/Redis をローカルで動かす |
| Gradle | Java のビルドツール（npm の Java 版） |

---

## 1. Docker Compose

### 本質
**「PostgreSQL と Redis をローカルで動かすために、Mac に直接インストールせず Docker コンテナで起動する」** ための仕組み。

### よく使うコマンド

| コマンド | 用途 |
| --- | --- |
| `docker compose up -d` | コンテナをバックグラウンド起動 |
| `docker compose ps` | コンテナの状態を確認 |
| `docker compose logs -f postgres` | postgres のログを流して見る |
| `docker compose exec postgres psql -U raisechat -d raisechat` | コンテナ内で psql 起動 |
| `docker compose stop` | 停止だけ（次回 `up` で再開） |
| `docker compose down` | 停止 + コンテナ削除（**データは残る**） |
| `docker compose down -v` | 停止 + コンテナ + **ボリュームも削除（データ消滅）** |

### `docker-compose.yml` の押さえどころ

| 設定 | 役割 |
| --- | --- |
| `image: postgres:17-alpine` | 使うイメージ。バージョンは固定する（latest は事故の元） |
| `ports: "5432:5432"` | "ホスト:コンテナ"。CLAUDE.md ポート規約に揃える |
| `environment: POSTGRES_*` | 初回起動時の DB・ユーザー・パスワード自動作成 |
| `volumes: postgres_data:/var/lib/postgresql/data` | **データ永続化**。これがないと再起動で全消失 |
| `healthcheck` | コンテナが「使える状態」かを判定（起動完了を待てる） |

### `.env` の運用

- `.env.example` → コミットする（テンプレート）
- `.env` → コミットしない（`.gitignore` で `*.env` 除外）
- `${VAR:-default}` で「環境変数 or デフォルト値」を表現

---

## 2. Spring Boot

### 本質
**「Java で API サーバーを書くためのフレームワーク。"家のキット" として骨組み部分を全部用意してくれている」**。自分のアプリ独自の処理だけ書けばいい。

### プロジェクトの作り方
```bash
curl -fsSL -G https://start.spring.io/starter.zip \
  -d type=gradle-project -d language=java \
  -d bootVersion=3.5.14 -d javaVersion=21 \
  -d groupId=com.raisechat -d artifactId=backend \
  -d dependencies=web,data-jpa,postgresql,flyway,validation,configuration-processor \
  -o /tmp/backend.zip && unzip /tmp/backend.zip -d backend
```

### よく使うコマンド

| コマンド | 用途 |
| --- | --- |
| `./gradlew bootRun` | 開発モードでアプリ起動 |
| `./gradlew build` | 本番用 jar ビルド |
| `./gradlew test` | テスト実行 |
| `./gradlew clean` | ビルド成果物クリア |

### `build.gradle` の押さえどころ

| ブロック | 役割 |
| --- | --- |
| `plugins` | Gradle の拡張機能（Java サポート、Spring Boot 連携） |
| `dependencies` | 使う外部ライブラリ一覧（`package.json` の親戚） |
| `repositories: mavenCentral()` | ライブラリの取得元（npm registry の親戚） |

### `starter` の考え方
`spring-boot-starter-XXX` は **「うどんセット」のような関連ライブラリの詰め合わせ**。
1 個入れるだけで関連 30 ライブラリが自動で入る。

| 入れた starter | できること |
| --- | --- |
| `web` | URL に対応する処理を書ける、JSON 返せる、Tomcat 内蔵 |
| `data-jpa` | Java オブジェクトで DB を操作（ORM） |
| `validation` | リクエストの入力チェック |
| `flyway` | DB マイグレーション（次節） |

### `application.yml` の押さえどころ

| 設定 | 何を指定しているか |
| --- | --- |
| `spring.datasource.url/username/password` | DB の場所と接続情報 |
| `spring.jpa.hibernate.ddl-auto: validate` | 起動時に Entity と DB のズレをチェック、DB は触らない |
| `spring.jpa.open-in-view: false` | N+1 問題を防ぐ慣習的な設定 |
| `spring.jpa.show-sql: true` | 開発中に SQL を見える化 |
| `spring.flyway.locations: classpath:db/migration` | V ファイルを探す場所 |
| `server.port: 8080` | Tomcat のポート（CLAUDE.md 規約） |

### `ddl-auto` の選択肢（重要）

| 値 | 起動時の挙動 |
| --- | --- |
| `create` / `create-drop` | DB を作り直す（遊び・テスト用） |
| `update` | 不足分を Hibernate が追加（開発初期向け、本番禁止） |
| `validate` ✅ | 比較するだけ、DB は触らない（Flyway と併用するならこれ） |
| `none` | チェックなし |

### 「Entity」とは
**`@Entity` 印を付けた Java クラス**。1 行 = 1 オブジェクトの対応で DB テーブルと結びつく。
`ddl-auto: validate` はこの Entity と DB を起動時に比較する。

---

## 3. Flyway とマイグレーション

### 本質
**「DB のスキーマ変更を SQL ファイルとして履歴管理する」** = Git の DB 版。
誰の Mac でも・本番でも、V ファイルを上から順に流せば同じ DB ができる。

### V ファイル名のルール

```text
V    1    __    init_schema    .sql
│    │    │       │             │
│    │    │       │             └ 固定
│    │    │       └────── 人間用メモ（snake_case）
│    │    └────── 区切り（アンダースコア 2 つ、固定）
│    └────── バージョン番号（昇順）
└────── V = 1 回だけ実行（他に R = Repeatable）
```

置き場所: `backend/src/main/resources/db/migration/`

### Flyway が起動時にやること

```text
1. flyway_schema_history テーブルを確認（無ければ作る）
2. db/migration/ の V ファイルをスキャン
3. 「未実行の V ファイル」だけ番号順に実行
4. 実行履歴を flyway_schema_history に記録
```

`flyway_schema_history` は Flyway 専用の台帳。`version`・`description`・`checksum`・`success` を記録。

### ⚠️ 鉄則: マージ済み V ファイルは編集禁止

- Flyway は V ファイルの `checksum`（指紋）を記録している
- 編集すると指紋が変わる → 次回起動時に **checksum mismatch エラーで起動不可**
- 変更したい時は **新しい V ファイル（V2, V3...）を追加** する（歴史は積む、書き換えない）
- ローカル開発中（main にマージ前）は編集 OK

### ローカルで作り直したい時

```bash
docker compose down -v        # ボリュームごと削除
docker compose up -d           # 起動し直し（空っぽの DB）
cd backend && ./gradlew bootRun  # Flyway が V1 から再実行
```

**本番環境では絶対やらない**（全データ消滅）。

---

## 4. V1__init_schema.sql の中身

### CREATE TABLE 構造

```sql
CREATE TABLE テーブル名 (
  列名  型  [NOT NULL] [DEFAULT 値] [PRIMARY KEY] ...,
  ...
  CONSTRAINT 制約名 CHECK (...),
  CONSTRAINT 制約名 FOREIGN KEY (...) REFERENCES ...
);
```

### 主なデータ型

| 型 | 用途 |
| --- | --- |
| `BIGINT` | 大きい整数（id 用） |
| `INT` | 普通の整数 |
| `VARCHAR(N)` | 最大 N 文字の文字列 |
| `TEXT` | 長文文字列 |
| `TIMESTAMPTZ` | タイムゾーン付き日時 |
| `tsvector` | 全文検索用の特殊型 |

### 主キー: `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- 自動連番（1, 2, 3...）
- `ALWAYS` = 手動指定禁止（事故防止）
- BIGINT = 920 京まで OK（INT の 21 億上限を最初から回避）

### CHECK 制約 = DB 側のバリデーション

```sql
CHECK (char_length(display_name) BETWEEN 1 AND 32)   -- 文字数範囲
CHECK (user_id ~ '^[A-Za-z0-9_-]{3,32}$')            -- 正規表現
CHECK (role IN ('OWNER','MEMBER'))                    -- ENUM 代わり（許可値の列挙）
```

**フロント・バックエンド・DB の三重防御。** DB は最後の砦。

### 外部キー (FK) と `ON DELETE` 動作

| 動作 | 親が消された時、子はどうなる | V1 での用途 |
| --- | --- | --- |
| `RESTRICT` | 削除を拒否 | **基本方針**（論理削除運用なので物理削除を防ぐ） |
| `CASCADE` | 子も一緒に削除 | reactions / mentions / attachments の親 FK |
| `SET NULL` | 子の参照列を NULL に書き換え | messages.parent_message_id（スレッド親） |

| FK | ON DELETE |
| --- | --- |
| 基本（owner, author, workspace 等の親への参照） | RESTRICT |
| 結合・子テーブル（members, reactions, mentions, attachments） | CASCADE |
| `messages.author_user_id` | RESTRICT（投稿者退会後もメッセージ残す） |
| `messages.parent_message_id` | SET NULL（スレッド親消失時に返信を孤児化しない） |

### 論理削除 (`deleted_at`)

**「行は残す、`deleted_at` に日時を入れて『削除済みマーク』にする」** 方式。

- 復元可能、過去データと整合、監査ログとして残せる
- 取得時は `WHERE deleted_at IS NULL` で除外
- V1 で論理削除を採用したテーブル: `users / workspaces / channels / messages / attachments / dm_rooms`

### `updated_at` 自動更新トリガー

**「行が UPDATE される直前に、自動で `updated_at = now()` に書き換える」** 仕組み。
アプリ側で書き忘れる事故を防ぐ。

```sql
-- 共通関数（1 個）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

-- 5 テーブルに紐付け
CREATE TRIGGER trg_<table>_updated_at
  BEFORE UPDATE ON <table>
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

設置テーブル: `users / workspaces / channels / messages / read_states`

### インデックス 4 種類

| 種類 | 用途 | 例 |
| --- | --- | --- |
| 普通 (UNIQUE) | 高速検索 + 一意性保証 | `UNIQUE INDEX ON users (user_id)` |
| 部分インデックス | 条件付き索引（無駄を省く） | `... WHERE deleted_at IS NULL` |
| 関数インデックス | 加工後の値で索引 | `INDEX ON users (LOWER(user_id))` |
| GIN | 全文検索用 | `INDEX ON messages USING GIN (body_tsv)` |

### 全文検索 (`body_tsv`)

```sql
body_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED
```

- `body` を INSERT/UPDATE するたびに、DB が自動で単語集合に分解して `body_tsv` に保存
- GIN インデックスと組み合わせて `body_tsv @@ to_tsquery(...)` で超高速検索
- `simple` 辞書は英数字向け、**日本語形態素解析は別途検討**（mecab / pgroonga など）

---

## 5. 起動時に動くもの

`./gradlew bootRun` を打ってから準備完了までの **約 1.7 秒** で、4 人の主役が順番に動く。

```text
1. HikariCP   → DB へのコネクションプール準備（接続線を確保）
        ↓
2. Flyway     → 未実行の V ファイルを適用（スキーマ最新化）
        ↓
3. Hibernate  → Entity と DB の整合性チェック（ddl-auto: validate）
        ↓
4. Tomcat     → 8080 ポートで HTTP リクエスト受付開始
        ↓
   "Started BackendApplication in X.X seconds"
```

**順番に意味がある**: DB を整えてから受付を開始することで、起動直後のリクエストでテーブルなしエラーを防ぐ。

---

## 6. よくあるエラーと対処

| エラーメッセージ（抜粋） | 段階 | 原因 | 対処 |
| --- | --- | --- | --- |
| `Connection refused` | HikariCP | DB コンテナが起動していない、ポート違い | `docker compose ps` で状態確認、`docker compose up -d` |
| `Address already in use: 8080` | Tomcat | 他のプロセスが 8080 を占有 | `lsof -ti:8080 \| xargs kill`（**別ポートに逃げない**） |
| `Address already in use: 5432` | Docker | ローカルに Postgres が直接インストールされている等 | `lsof -ti:5432 \| xargs kill` |
| `Unsupported Database: PostgreSQL 17` | Flyway | `flyway-database-postgresql` 依存漏れ | `build.gradle` で `implementation 'org.flywaydb:flyway-database-postgresql'` を追加（Initializr が含めてる場合は不要） |
| `Migration checksum mismatch` | Flyway | マージ済み V ファイルを編集してしまった | ローカル: `docker compose down -v` で作り直し。本番: 別 V ファイルで打ち消す |
| `Schema-validation: missing column` | Hibernate | Entity と DB のカラムが食い違う | Entity 側 or V ファイル側を直す |
| `Schema-validation: missing table` | Hibernate | テーブル自体がない | Flyway が走っているか確認、`flyway_schema_history` を psql で確認 |

---

## 7. 確定した設計方針（V2 以降も踏襲するルール）

- DB ボリュームは **名前付きボリューム**（`postgres_data` / `redis_data`）
- 主キーは `BIGINT GENERATED ALWAYS AS IDENTITY`
- ENUM は **VARCHAR + CHECK** で表現（PostgreSQL ENUM 型は使わない）
- 論理削除は `deleted_at TIMESTAMPTZ`
- `updated_at` は共通トリガー `set_updated_at()` で自動更新
- FK の ON DELETE は **基本 RESTRICT、結合系のみ CASCADE**、`messages.author_user_id` は RESTRICT、`messages.parent_message_id` は SET NULL
- Spring Boot 依存は **必要になってから追加**（最初は最小構成）
- Spring Boot バージョンは **3.x 系最新の 3.5.14**（Initializr のサポートが `>=3.5.0`）

---

## 8. AI エンジニアとしての到達ライン

| レベル | 内容 | 目指す？ |
| --- | --- | --- |
| A | 各部品の名前と役割が言える（Flyway / JPA / Docker など） | ✅ マスト |
| B | エラーや動作を見て原因を推測できる | ✅ マスト |
| C | コードを読んで「何をしているか」が分かる | ✅ マスト |
| D | AI に「こう書き換えて」と具体的に指示できる | ✅ マスト |
| E | 自分で 1 から書ける（手書きでスラスラ） | △ 副産物として徐々に |

**ゴールは A〜D まで**。E は AI に任せる前提で OK。

---

## 9. 関連リソース

- 設計書: [docs/database-design.md](../database-design.md)
- 要件定義: [docs/requirements.md](../requirements.md)
- 機能要件: [docs/functional-requirements.md](../functional-requirements.md)
- このノートの元になった実装: PR #6（chore: バックエンド初期化と初期スキーマを追加）
- 公式ドキュメント:
  - Spring Boot: <https://docs.spring.io/spring-boot/>
  - Flyway: <https://documentation.red-gate.com/fd>
  - PostgreSQL 17: <https://www.postgresql.org/docs/17/>
  - Spring Initializr: <https://start.spring.io>
