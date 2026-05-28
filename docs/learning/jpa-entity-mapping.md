# JPA Entity / Repository 学習ノート（チートシート）

PR #9 (B-1) で実装した「コア 5 テーブルの Entity と Repository」について、**今やっていたのは何か / どう繋がっているか / 困った時にどこを見ればいいか** を後から戻ってこれる形にまとめたもの。

---

## 0. 全体像（30 秒で復習）

```text
┌─────────────────────────────────────┐
│  ブラウザ (Next.js)                 │  ← ユーザーが見る画面
└──────────────┬──────────────────────┘
               │ HTTP リクエスト
               ↓
┌─────────────────────────────────────┐
│  Spring Boot (バックエンド)         │
│  - Controller   リクエスト受け取り  │  ← まだ未実装
│  - Service      ビジネスロジック    │  ← まだ未実装
│  - Repository   データ出し入れの窓口│  ★今回作った
│  - Entity       DB 表との橋渡し     │  ★今回作った
└──────────────┬──────────────────────┘
               │ JPA が自動で SQL に翻訳
               ↓
┌─────────────────────────────────────┐
│  PostgreSQL (Docker)                │
│  - 14 テーブル (V1 で作成済み)      │
└─────────────────────────────────────┘
```

→ 今回は **「Java と DB を繋ぐ橋渡し層」** を、コア 5 テーブル分作った。

---

## 1. 役割を 1 行で

| 部品 | 一言 |
| --- | --- |
| **Entity** | DB の 1 テーブルを Java のクラスとして表現した「設計図」 |
| **Repository** | そのテーブルにデータを出し入れする「窓口」（インターフェース 1 個書くだけで Spring が自動実装してくれる） |

→ 細かいアノテーション (`@Entity`, `@ManyToOne` 等) は「設計図に書き込む細かいラベル」。**自分で全部覚える必要はなく、AI に書かせて OK**。

---

## 2. 今回作ったファイル一覧（DB 表との対応）

| ドメイン | Entity ファイル | Repository ファイル | 対応する DB テーブル |
| --- | --- | --- | --- |
| user | `user/User.java` | `user/UserRepository.java` | `users` |
| workspace | `workspace/Workspace.java` | `workspace/WorkspaceRepository.java` | `workspaces` |
| workspace | `workspace/WorkspaceMember.java` | `workspace/WorkspaceMemberRepository.java` | `workspace_members` |
| workspace | `workspace/WorkspaceRole.java` (enum) | — | role 列の値 `OWNER`/`MEMBER` |
| channel | `channel/Channel.java` | `channel/ChannelRepository.java` | `channels` |
| channel | `channel/ChannelType.java` (enum) | — | type 列の値 `PUBLIC`/`PRIVATE` |
| message | `message/Message.java` | `message/MessageRepository.java` | `messages` |

→ パッケージは **「ドメイン別」** で配置（実務で増えている形）。`com.raisechat.user/`, `workspace/`, `channel/`, `message/`。

---

## 3. アノテーションの押さえどころ（最小限）

| アノテーション | やってくれること |
| --- | --- |
| `@Entity` | 「このクラスは DB テーブルにマッピングする」と宣言 |
| `@Table(name = "users")` | 対応するテーブル名を指定（Java 単数 / SQL 複数の橋渡し） |
| `@Id` + `@GeneratedValue(strategy = IDENTITY)` | 主キー。値の採番は DB に任せる |
| `@Column(name, nullable, length)` | DB 列との対応。型・NOT NULL・長さも一致させる |
| `@ManyToOne` + `@JoinColumn` | **外部キー**を表現。`@ManyToOne` で「多 対 1」の関係を、`@JoinColumn` でその FK 列を指定 |
| `@Enumerated(EnumType.STRING)` | enum の値を **文字列で DB に保存**（`ORDINAL` は使わない、後述） |

### `insertable = false, updatable = false` が付くケース

```java
@Column(name = "created_at", insertable = false, updatable = false)
private OffsetDateTime createdAt;
```

→ **「DB の DEFAULT やトリガーで自動的に書かれる列」** に付ける宣言。これがないと JPA が `null` を INSERT しようとして NOT NULL 違反になる。

`created_at` / `updated_at` / `joined_at` がこのパターン。

---

## 4. Repository は「インターフェースを書くだけ」

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);
}
```

これだけで以下のメソッドが**自動的に**使える:

| メソッド | 裏で発行される SQL |
| --- | --- |
| `userRepository.save(user)` | `INSERT INTO users ...` or `UPDATE users ...` |
| `userRepository.findById(1L)` | `SELECT * FROM users WHERE id = 1` |
| `userRepository.findAll()` | `SELECT * FROM users` |
| `userRepository.deleteById(1L)` | `DELETE FROM users WHERE id = 1` |
| `userRepository.findByUserId("alice")` | `SELECT * FROM users WHERE user_id = 'alice'` ← メソッド名から自動生成 |

→ Spring が起動時に「動的プロキシ」という技術で実装クラスを内部生成してくれる。**自分は SQL を一切書いていない**。

### メソッド名から SQL を自動生成するルール

| メソッド名 | 生成される WHERE |
| --- | --- |
| `findByUserId(x)` | `WHERE user_id = x` |
| `findByDeletedAtIsNull()` | `WHERE deleted_at IS NULL` |
| `findByXxxAndYyyIsNull(a)` | `WHERE xxx = a AND yyy IS NULL` |
| `existsByUserId(x)` | `SELECT 1 FROM ... WHERE user_id = x` |
| `countByDeletedAtIsNotNull()` | `SELECT COUNT(*) ... WHERE deleted_at IS NOT NULL` |

---

## 5. よくあるハマりポイント

### IDE が Lombok を一時認識しない

VSCode の Java 拡張が、`build.gradle` に Lombok を追加した直後に**赤線を出す**ことがある。

→ **Gradle ビルドが通れば実体は OK**。`./gradlew compileJava` で確認できる。
→ IDE の赤線を消すには `Cmd + Shift + P` → `Java: Clean Java Language Server Workspace`。

### Spring Boot 起動時に `Schema-validation` エラー

```text
Schema-validation: missing column [xxx] in table [yyy]
```

→ **Entity の列宣言と DB スキーマがズレている**サイン。

| よくある原因 | 対処 |
| --- | --- |
| Java で `@Column(name = "userId")` と書いた（snake_case ではない） | `name = "user_id"` に直す |
| `nullable = false` 忘れ | スキーマに合わせる |
| Entity に書いた列が DB に存在しない | 列名のタイプミス、または Flyway V ファイルの追加忘れ |

### `@Data` を Entity に使うと地獄

Lombok の **`@Data` は Entity には使わない**。`equals/hashCode` が JPA の Lazy loading と組み合わさって意味不明なバグになる。

→ Entity では `@Getter @Setter @NoArgsConstructor` を個別に書く。

### `@Enumerated(EnumType.ORDINAL)` を使うと未来に死ぬ

```java
@Enumerated(EnumType.ORDINAL)   // ← 絶対これ書くな
```

→ enum の並びを変えただけで DB に残っている過去データの意味が変わる。**実務では問答無用で `EnumType.STRING`**。

---

## 6. 起動確認のやり方

```bash
# Docker (PostgreSQL/Redis) が起動しているか
docker compose ps

# Entity と DB スキーマの整合チェック（ddl-auto: validate）
cd backend && ./gradlew test
# → BUILD SUCCESSFUL なら、Entity と DB がピッタリ整合
```

`./gradlew bootRun` でも起動するが、起動チェックだけなら `test` の方が早い（テストクラスが Spring コンテキストを立ち上げて閉じるだけだから）。

---

## 7. 次に進む時の状態

| 状態 |
| --- |
| ✅ コア 5 テーブル（users / workspaces / workspace_members / channels / messages）の Entity + Repository（**B-1**） |
| ✅ シードデータ `R__seed_dev.sql`（**A**） |
| ✅ 認証 API（refresh_tokens 含む）（**C**） |
| ✅ 残り 7 テーブル（dm_rooms / dm_members / attachments / reactions / mentions / read_states / workspace_invites）の Entity + Repository、および messages.dm_room_id マッピング（**B-2**） |
| ✅ Spring Boot が起動可能（validate 通過） |
| ⬜ 残りの設計書（API 設計 / 画面遷移 等）は **D** タスクで対応 |

---

## 8. パッケージ分割の判断軸（B-2 で追加）

B-2 で 7 テーブル分を追加したとき、「どのフォルダに置くか？」を判断する場面が出てきた。基準は 1 つだけ。

> **そのテーブルは単独で存在できるか？**

| 判定 | 配置 | 例 |
| --- | --- | --- |
| **主役**（単独で意味を持つ） | 専用フォルダを新設 | `dm_rooms` → `com.raisechat.dm/` 新設 |
| **脇役**（必ず親にぶら下がる） | 親と同じフォルダに同居 | `attachments` → `com.raisechat.message/`、`workspace_invites` → `com.raisechat.workspace/` |

判定の仕方:

- DM ルームは、メッセージが 1 件も無くてもルーム自体は存在できる（2 人の関係性として独立）→ **主役**
- 添付ファイルは、必ず `message_id` が NOT NULL。「どのメッセージの添付か」が無いと存在不可能 → **脇役**

### なぜこの軸でいいのか

コードを読む人が「DM 関連を触りたい」と思ったとき、`dm/` フォルダを開けば全部揃っている、という直感が成立する。脇役を全部独立フォルダにすると `attachment/`, `reaction/`, `mention/`... と細かくなりすぎて、逆にどこに何があるか見えなくなる。

### 進化したときの効果

将来「DM だけ別サービスに切り出したい」みたいな話が出たとき、**主役単位で切ったパッケージがそのまま分離単位の候補**になる。最初から正しく切っておくと、後から動かすコストが小さい。

---

## 9. DB が守れるルールはアプリで書かない（B-2 で追加）

B-2 では `messages` テーブルに **「channel_id か dm_room_id の片方だけ」** という XOR 制約があった。

```sql
CONSTRAINT messages_channel_or_dm CHECK (
  (channel_id IS NOT NULL) <> (dm_room_id IS NOT NULL)
)
```

> `<>` は「等しくない」。両方 NOT NULL（true vs true）も、両方 NULL（false vs false）もダメ。

**判断**: Java の Entity 側で「両方セットされたら例外を投げる」コードは書かなかった。

### 理由: 二重管理になるから

| ルールの種類 | どこで守る？ |
| --- | --- |
| **データの整合性**（NULL不可 / UNIQUE / FK / XOR / CHECK） | **DB 制約**（FK / NOT NULL / UNIQUE / CHECK） |
| **ビジネスロジック**（送信レートリミット / 禁止語フィルタ） | **アプリ層**（Service / Validator） |
| **ユーザー入力の即時フィードバック** | フロントエンド + アプリ層（DB はラスト防衛線） |

DB の CHECK 制約は、JPA 経由でも、psql で直接 INSERT しても、別アプリから接続しても **必ず効く**。アプリで二重に書くと「JPA 経由のときだけ守られるルール」になり、整合性が DB / アプリ どちらかにズレる原因になる。

### 今回 DB に任せた具体例

| ルール | DB 側の表現 |
| --- | --- |
| messages は channel か dm のどちらか片方 | CHECK 制約 |
| read_states も channel か dm のどちらか片方 | CHECK 制約 + 部分 UNIQUE INDEX |
| dm_rooms は user_a < user_b（ペア一意のため順序固定） | CHECK 制約 |
| attachments.mime_type は 5 値固定 | CHECK 制約（Java 側は String のまま） |

→ Entity は素のマッピングに集中、バリデーションは DB に任せる。

---

## 10. Repository は最小限から始める（B-2 で追加）

B-2 で 7 つの Repository を作ったが、**5 つは中身ゼロ**（`JpaRepository<E, Long>` 継承だけ）。

```java
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
```

これだけでも `save` / `findById` / `findAll` / `deleteById` 等は自動で使える（セクション 4 参照）。

### 検索メソッドを追加したのは 2 つだけ

```java
// DmRoomRepository
Optional<DmRoom> findByWorkspaceIdAndUserAIdAndUserBId(Long ..., Long ..., Long ...);

// WorkspaceInviteRepository
Optional<WorkspaceInvite> findByTokenHash(String tokenHash);
```

### なぜ最小限にしたか — YAGNI 原則

> **You Aren't Gonna Need It** — 必要になるまで書くな

「将来こんなメソッドが要りそう」で先回りに書くと、実際は呼ばれずに残ったり、API 仕様が固まって書き直しになることが多い。書かないのが正解。

### 「明白な一意検索 2 つだけ」は何が明白なのか

DB スキーマを見れば、**今すぐ使うことが確定している検索**が分かる:

- `workspace_invites.token_hash` に **UNIQUE INDEX** → 招待リンク受諾フローで必ず使う
- `dm_rooms (workspace_id, user_a_id, user_b_id)` に **UNIQUE INDEX** → 「この 2 人の DM ルーム既にある？」で必ず使う

**UNIQUE INDEX が張られている = 設計者が一意検索 API を想定している証拠**。先回りで書いても無駄になりません。

逆に「reactions をメッセージ ID で集計する」のような検索は、集計方法（COUNT? GROUP BY emoji?）が API 仕様次第で変わる。今書くと書き直しリスクが高いので空のまま。

### 一般則

| 状況 | Repository に書く？ |
| --- | --- |
| UNIQUE INDEX が張られているキーでの一意検索 | **書く**（仕様確定済み） |
| 「使うかも」レベルの想像メソッド | **書かない** |
| API 実装時に必要になったメソッド | **そのとき書く** |

→ Entity の流儀（`@Getter @Setter` 並び等）は型で揃えるけど、Repository のメソッドは要件が出るまで増やさない、という線引き。
