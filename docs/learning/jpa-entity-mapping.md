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
| ✅ コア 5 テーブル（users / workspaces / workspace_members / channels / messages）の Entity + Repository |
| ✅ Spring Boot が起動可能（validate 通過） |
| ⬜ 残り 9 テーブル（dm_rooms / dm_members / messages の dm 関連 / attachments / reactions / mentions / read_states / workspace_invites / refresh_tokens）は **B-2** で対応 |
| ⬜ シードデータ（`R__seed_dev.sql`）は **A** タスクで対応 |
| ⬜ 認証 API は **C** タスクで対応 |
