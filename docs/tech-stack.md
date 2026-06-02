# 技術スタック — RaiseChat（採用技術・バージョン一覧）

関連: [要件定義書](requirements.md) / [機能要件書](functional-requirements.md) / [API 設計書](api-design.md) / [データベース設計書](database-design.md) / [リアルタイム通信設計書](realtime-design.md) / [Redis キャッシュ戦略設計書](cache-strategy.md)

---

## 0. はじめに

### 0.1 本書のゴール

RaiseChat で **実際に採用している技術とそのバージョン** を 1 枚にまとめる。
バージョンは推測ではなく、リポジトリ内の実ファイルを正とする。

| 正とするファイル | 対象 |
| --- | --- |
| [`backend/build.gradle`](../backend/build.gradle) | バックエンドの言語・フレームワーク・ライブラリ |
| [`frontend/package.json`](../frontend/package.json) | フロントエンドの言語・フレームワーク・ライブラリ |
| [`docker-compose.yml`](../docker-compose.yml) | ミドルウェア（DB / キャッシュ / S3 エミュレータ）|

### 0.2 本書のスコープ

| 範囲 | 状態 |
| --- | --- |
| フロント / バックエンドの採用技術・バージョン | ✅ 本書で確定 |
| ミドルウェア（PostgreSQL / Redis / S3）の採用技術 | ✅ 本書で確定 |
| ビルド・開発ツール | ✅ 本書で確定 |
| リアルタイム通信 / キャッシュ / 認証の **設計詳細** | ⛔ 各設計書に委譲（本書は採用技術の明示のみ）|
| インフラ・デプロイ構成（AWS / Render 等）| ⛔ 本書スコープ外（後続のインフラ設計で確定）|

### 0.3 バージョン表記の見方

- セマンティックバージョニング前提。`^` / `~` 付きは package.json の宣言値、実解決値は lockfile に従う。
- 「役割」は RaiseChat 内での使われ方を一言で示す。詳細は各設計書を参照。

---

## 1. 全体構成

```
┌─────────────┐   HTTPS (REST/JSON)    ┌──────────────────┐
│  ブラウザ     │ ─────────────────────▶ │  Spring Boot     │
│  Next.js 14  │                        │  (port 8080)     │
│  React 18    │ ◀═══════════════════▶  │                  │
└─────────────┘  WebSocket(STOMP/      │  - REST API      │
       (port 3000)   SockJS)            │  - WebSocket     │
                                        │  - JWT 認証      │
                                        └────────┬─────────┘
                                                 │
                       ┌─────────────────────────┼─────────────────────────┐
                       │                         │                         │
                  ┌────▼─────┐            ┌──────▼──────┐           ┌──────▼──────┐
                  │PostgreSQL │            │   Redis 7   │           │   S3        │
                  │   17      │            │ キャッシュ /  │           │ (LocalStack │
                  │ 永続化     │            │ Pub-Sub     │           │  /本番 AWS) │
                  └──────────┘            └─────────────┘           └─────────────┘
```

- **フロント ⇄ バックエンド**: 通常データは REST/JSON、リアルタイム更新は WebSocket(STOMP over SockJS)。
- **Redis**: キャッシュ（[cache-strategy.md](cache-strategy.md)）と、複数インスタンス間のイベント配信用 Pub-Sub（[realtime-design.md](realtime-design.md)）を兼ねる。
- **S3**: アバター画像（F-02）・ファイル添付（F-10）の保存先。ローカルは LocalStack、本番は AWS S3 を想定。

---

## 2. フロントエンド

`frontend/package.json` より。

| 技術 | バージョン | 役割 |
| --- | --- | --- |
| Next.js | 14.2.35 | React フレームワーク（App Router）。port 3000 |
| React / React DOM | 18 | UI ライブラリ |
| TypeScript | 5 | 型付き JavaScript |
| Tailwind CSS | 3.4.1 | ユーティリティ CSS |
| @tanstack/react-query | 5.100.14 | サーバー状態管理・データフェッチ／キャッシュ |
| @stomp/stompjs | 7.3.0 | STOMP クライアント（WebSocket 上のメッセージング）|
| sockjs-client | 1.6.1 | WebSocket フォールバック付きトランスポート |
| react-markdown | 10.1.0 | メッセージの Markdown レンダリング |
| remark-gfm | 4.0.1 | GitHub Flavored Markdown（表・打消し線等）|
| rehype-sanitize | 6.0.0 | Markdown レンダリング時の XSS サニタイズ |
| emoji-picker-react | 4.19.1 | リアクション用の絵文字ピッカー |
| date-fns | 4.2.1 | 日時フォーマット |
| lucide-react | 1.16.0 | アイコン |
| clsx / tailwind-merge | 2.1.1 / 3.6.0 | className 条件付き結合・Tailwind クラス競合解決 |

---

## 3. バックエンド

`backend/build.gradle` より。

| 技術 | バージョン | 役割 |
| --- | --- | --- |
| Java | 21 | 言語（Gradle toolchain で固定）|
| Spring Boot | 3.5.14 | アプリケーションフレームワーク。port 8080 |
| spring-boot-starter-web | (BOM 準拠) | REST API |
| spring-boot-starter-data-jpa | (BOM 準拠) | DB アクセス（Hibernate / JPA）|
| spring-boot-starter-validation | (BOM 準拠) | リクエストバリデーション |
| spring-boot-starter-security | (BOM 準拠) | 認証・認可 |
| spring-boot-starter-websocket | (BOM 準拠) | WebSocket / STOMP サーバー |
| spring-boot-starter-data-redis | (BOM 準拠) | Redis 連携（キャッシュ / Pub-Sub）|
| JJWT (jjwt-api/impl/jackson) | 0.12.6 | JWT の発行・検証 |
| AWS SDK for Java v2 (s3) | BOM 2.45.1 | S3 連携（アバター・添付）|
| Flyway (core + database-postgresql) | (BOM 準拠) | DB マイグレーション |
| PostgreSQL JDBC Driver | (BOM 準拠) | DB ドライバ（runtimeOnly）|
| Lombok | (BOM 準拠) | ボイラープレート削減 |

> 「(BOM 準拠)」は、Spring Boot 3.5.14 の dependency-management（`io.spring.dependency-management` 1.1.7）が解決するバージョンを使用していることを示す。AWS SDK のみ専用 BOM `software.amazon.awssdk:bom:2.45.1` でバージョンを固定している。

---

## 4. ミドルウェア・データストア

`docker-compose.yml` より。ローカル開発は Docker で起動する。

| 技術 | イメージ / バージョン | 役割 | ポート |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:17-alpine` | 永続データストア | 5432 |
| Redis | `redis:7-alpine` | キャッシュ / Pub-Sub | 6379 |
| LocalStack | `localstack/localstack:3` | AWS S3 のローカルエミュレータ | 4566 |

- **LocalStack** は `storage` profile 付きのため、通常の `docker compose up -d` では起動しない。S3 を使う機能（F-02 / F-10）を触るときだけ `docker compose --profile storage up -d localstack` で明示起動する。本番は実 AWS S3 をエンドポイント差し替えで利用する想定。

---

## 5. リアルタイム通信

| 技術 | 採用箇所 | 役割 |
| --- | --- | --- |
| WebSocket | フロント ⇄ バックエンド | 常時接続 |
| STOMP | 〃 | メッセージング・プロトコル |
| SockJS | 〃 | WebSocket 不可環境向けフォールバック |
| Redis Pub-Sub | バックエンド間 | 複数インスタンスへのイベント配信 |

> 接続 URL・destination 設計・認証・JSON スキーマ等の **設計詳細は [realtime-design.md](realtime-design.md) に委譲する**。本書は採用技術の明示にとどめる。

---

## 6. 認証・セキュリティ

| 技術 | 役割 |
| --- | --- |
| Spring Security | 認証フィルタ・認可ルール |
| JJWT 0.12.6 | JWT の発行・署名・検証 |
| rehype-sanitize（フロント）| Markdown 描画時の XSS 対策 |

> 認証フロー・トークンの有効期限・保存方法等は API 設計書および実装に従う。本書は採用技術の明示にとどめる。

---

## 7. ビルド・開発ツール

| 技術 | バージョン | 用途 |
| --- | --- | --- |
| Gradle | (wrapper 同梱) | バックエンドのビルド・依存解決 |
| Flyway | (BOM 準拠) | DB スキーマのバージョン管理・マイグレーション |
| npm | (Node 同梱) | フロントエンドの依存管理・スクリプト実行 |
| ESLint / eslint-config-next | 8 / 14.2.35 | フロントの静的解析 |
| PostCSS | 8 | Tailwind のビルド |
| Docker / Docker Compose | — | ミドルウェアのローカル起動 |

---

## 8. インフラ・デプロイ

本書のスコープ外。方針は [インフラ設計書](infrastructure.md) で確定済み（具体的な IaC / パイプライン / Playbook は実装フェーズで詰める）。

| 項目 | 状態 |
| --- | --- |
| ホスティング | ✅ AWS（[infrastructure.md](infrastructure.md)）|
| IaC | ✅ Terraform（[`infra/terraform/`](../infra/terraform/README.md)。state は S3 + DynamoDB / bootstrap で管理）|
| 冗長化構成（バックエンド複数台）| ✅ ECS Fargate 複数タスク + Redis Pub-Sub（[infrastructure.md §6](infrastructure.md) / [realtime-design.md](realtime-design.md)）|
| 自動デプロイ / CI/CD | ✅ GitHub Actions → ECR → ECS（方針確定。[infrastructure.md §7](infrastructure.md)）|
| 構成管理（Ansible 等）| ✅ Fargate＋運用 EC2(bastion) を Ansible 題材に（[infrastructure.md §8](infrastructure.md)）|

---

## 9. バージョン管理方針

- 本書のバージョンは **実ファイル（build.gradle / package.json / docker-compose.yml）を唯一の正** とする。
- 依存追加・バージョン更新を行ったら、本書の該当表も同じ PR で更新する（CLAUDE.md の「採用技術が確定したら docs/tech-stack.md も同時に更新する」ルールに対応）。
- メジャーバージョンを上げる際は、互換性影響を PR 説明に記載する。
