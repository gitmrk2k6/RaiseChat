# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## RaiseChat — Claude Code ルール

## 命名規則（Conventional Commits ベース）

すべての命名で以下の `type` を統一して使う。

| type | 用途 |
|------|------|
| `feat` | 新機能追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメント更新 |
| `refactor` | リファクタリング |
| `chore` | 設定変更・依存関係更新 |
| `test` | テスト追加・修正 |

### イシュータイトル

```text
<type>: <作業の概要>
```

例:

```text
feat: F-01 ユーザー認証APIの実装
fix: ログイン時にエラーが発生する問題
docs: 要件定義書の作成
```

### ブランチ名

```text
<type>/issue-<番号>-<短い説明（英語）>
```

例:

```text
feat/issue-1-f01-auth
fix/issue-5-fix-login-error
docs/issue-2-requirements
```

### コミットメッセージ

```text
<type>: <変更内容の概要>
```

例:

```text
feat: F-01 ユーザー認証APIを追加
fix: ログイン時のエラーを修正
docs: 要件定義書を追加
```

### プルリクエストタイトル

```text
<type>: <作業の概要> (#<イシュー番号>)
```

例:

```text
feat: F-01 ユーザー認証を実装 (#1)
fix: ログイン時のエラーを修正 (#5)
```

---

## GitHub ワークフロー（必ず守ること）

### 1. 作業開始前に必ずイシューを作る

```bash
gh issue create --title "<type>: <作業の概要>" --body "<詳細説明>" --label "<ラベル>"
```

- イシューなしで作業を開始してはならない

### 2. ブランチを作る

```bash
git checkout -b <type>/issue-<番号>-<説明>
```

### 3. main への直接プッシュ禁止

- `main` ブランチへの直接コミット・プッシュは禁止（GitHub 側でも保護設定する想定）
- 作業は必ずフィーチャーブランチで行い、プルリクエスト経由でマージする

### 4. プルリクエストのルール

- PR 本文に `Closes #<イシュー番号>` を必ず記載する
- PR のマージはユーザーが行う。Claude は PR を作成して URL を報告するまでが役割

```bash
gh pr create --title "<type>: <概要> (#<番号>)" --body "Closes #<番号>"
```

### 5. 作業の流れまとめ

```text
1. gh issue create でイシュー作成
2. git checkout -b <type>/issue-<番号>-<説明> でブランチ作成
3. 作業・コミット（Conventional Commits 形式）
4. gh pr create でプルリクエスト作成・URL を報告
5. マージ後にブランチ削除
```

---

## サーバー起動ルール（必ず守ること）

### 指定ポート

| サーバー | ポート |
| ------- | ------ |
| フロントエンド（Next.js） | `3000` |
| バックエンド（Spring Boot） | `8080` |
| データベース（PostgreSQL） | `5432` |
| キャッシュ / Pub-Sub（Redis） | `6379` |

### ポート競合時の対処

**一時的に別のポートで起動することは禁止。** ポート競合が発生した場合は必ず以下の手順で対処する。

1. 競合しているプロセスを停止する

```bash
kill $(lsof -ti:<ポート番号>)
```

2. 必ず指定されたポートで起動し直す

別ポートでの起動（例: `8081`、`3002`）は行わない。

### 起動順序

```bash
# 1. PostgreSQL / Redis（Docker）
docker compose up -d

# 2. バックエンド（port 8080）
cd backend && ./gradlew bootRun

# 3. フロントエンド（port 3000）
cd frontend && npm run dev
```

---

## 技術スタック方針（暫定）

| レイヤー | 主要技術 |
| ------- | ------- |
| フロントエンド | Next.js 14.x + TypeScript + Tailwind CSS |
| バックエンド | Spring Boot 3.x + Java 21 |
| リアルタイム通信 | WebSocket（STOMP over SockJS） |
| データベース | PostgreSQL 17（Docker） |
| キャッシュ / Pub-Sub | Redis 7 系（Docker） |
| 認証 | Spring Security + JWT（JJWT） |
| 画像 / ファイルストレージ | AWS S3（想定） |
| インフラ | 後続フェーズで決定（AWS / Render を検討） |

- バージョン・採用技術が確定したら `docs/tech-stack.md` も同時に更新する

---

## プロジェクト概要

- **コース**: RaiseTech AI エンジニアコース（上級編）
- **テーマ**: Slack 風チャット Web アプリケーション
- **上級編で扱う技術要素**: WebSocket リアルタイム通信 / Redis キャッシュ戦略 / 冗長化構成 / 自動デプロイ / Ansible / Claude Code 自動コードレビュー
- **ドキュメント**: `docs/` ディレクトリ参照
  - [要件定義書](docs/requirements.md)
  - [機能要件書](docs/functional-requirements.md)
  - [「Slack 風」の捉え方](docs/why-slack.md)
