# インフラ設計書 — RaiseChat（AWS 構成）

関連: [要件定義書](requirements.md) / [機能要件書](functional-requirements.md) / [技術スタック](tech-stack.md) / [リアルタイム通信設計書](realtime-design.md) / [Redis キャッシュ戦略設計書](cache-strategy.md) / [データベース設計書](database-design.md)

---

## 0. はじめに

### 0.1 本書のゴール

RaiseChat を **本番運用するための AWS インフラ構成** を確定する。
[技術スタック設計書](tech-stack.md) §8 が後続のインフラ設計へ委譲した 4 項目（ホスティング / 冗長化 / CI-CD / 構成管理）を、本書で **方向性レベル** まで定める。具体的なコード（CloudFormation / Terraform の HCL、GitHub Actions の YAML、Ansible Playbook 等）は実装フェーズ（[要件定義書 §10](requirements.md) フェーズ⑤）で詰める。

### 0.2 本書のスコープ

| 範囲 | 状態 |
| --- | --- |
| ホスティング先（AWS）の確定 | ✅ 本書で確定 |
| 全体構成図・論理要素の AWS サービスへのマッピング | ✅ 本書で確定 |
| ネットワーク構成（VPC / サブネット / SG）の方針 | ✅ 本書で確定（詳細値は実装で確定）|
| 冗長化・可用性の構成方針 | ✅ 本書で確定 |
| CI/CD（自動デプロイ）の方針 | ✅ 本書で確定（YAML は実装に委譲）|
| 構成管理（Ansible）の適用範囲 | ✅ 本書で確定（Playbook は実装に委譲）|
| 監視・シークレット管理の方針 | ✅ 本書で確定 |
| リアルタイム通信 / キャッシュ / 認証の **設計詳細** | ⛔ 各設計書に委譲 |
| IaC コード・パイプライン定義・Playbook の **実体** | ⛔ 実装フェーズに委譲 |
| コスト最適化・本番チューニングの数値確定 | ⛔ 運用フェーズに委譲 |

### 0.3 正とする前提

本書は以下を所与とし、再定義しない。

| 前提 | 出典 |
| --- | --- |
| 非機能要件（リアルタイム性 / 可用性 / ストレージ / オブザーバビリティ）| [要件定義書 §4](requirements.md) |
| 冗長化の論理設計（複数インスタンス + Redis Pub-Sub 橋渡し）| [リアルタイム通信設計書 §7・§10](realtime-design.md) |
| 想定スケール（同時数十〜数百セッション、10 msg/sec、インスタンス 1〜3）| [リアルタイム通信設計書 §10.1](realtime-design.md) |
| 採用技術・バージョン | [技術スタック設計書](tech-stack.md) |
| ローカル開発のミドルウェア構成 | [`docker-compose.yml`](../docker-compose.yml) |

---

## 1. インフラに効く要件の整理

[要件定義書 §4](requirements.md) の非機能要件のうち、インフラ構成を左右するものを抽出する。

| 要件 | 内容 | インフラへの含意 |
| --- | --- | --- |
| リアルタイム性 | 送信から受信表示まで 1 秒以内 | バックエンドと Redis を同一リージョン・低レイテンシ網に配置。WebSocket を終端できる LB が必要 |
| スケーラビリティ | バックエンド複数インスタンスでも全クライアントへ正しくブロードキャスト | アプリ層を水平スケール可能なコンテナ実行基盤＋共有 Redis Pub-Sub |
| 可用性 | 各層が単一障害点とならない構成を **目標**（コストとのバランス）| アプリ複数タスク・DB/Redis の Multi-AZ。学習用途のため過剰冗長は避ける |
| ストレージ | 画像・動画はオブジェクトストレージに保存しアプリサーバーに持たない | S3。アプリはステートレス（ファイルをローカルに保持しない）|
| セキュリティ | JWT 認証・所属メンバーのみアクセス | DB/Redis は private 配置。外部公開は LB のみ |
| オブザーバビリティ | 構造化ログを出力し後続で CloudWatch 等へ集約 | ログを標準出力 → CloudWatch Logs に集約 |
| 性能 | 通常操作 2 秒以内 / 1 チャンネル 1 万件でも劣化しない | DB にインデックス（[データベース設計書](database-design.md)）、高頻度参照は Redis キャッシュ（[キャッシュ戦略](cache-strategy.md)）|

### 1.1 想定スケール（MVP）

[リアルタイム通信設計書 §10.1](realtime-design.md) に従う。

- 同時接続: 数十 〜 数百セッション
- メッセージレート: 10 msg/sec 程度
- バックエンドインスタンス数: 1 〜 3

この規模を前提に、**過剰な冗長化は避けつつ単一障害点を段階的に潰せる** AWS マネージド構成を採る。

---

## 2. インフラ全体方針（AWS 前提）

RaiseChat の本番インフラは **AWS 上に構築する**。基本スタンスは次の 3 点。

- **マネージドサービス優先**: DB（RDS）・キャッシュ（ElastiCache）・オブジェクトストレージ（S3）は自前管理せず AWS マネージドに任せ、運用負荷を下げる。
- **アプリ層はステートレス・コンテナ実行**: バックエンド（Spring Boot）はコンテナ化して水平スケールする。状態（セッション・ファイル・キャッシュ）はアプリ外（Redis / S3 / JWT）に持たせる。
- **Multi-AZ で単一障害点を段階的に排除**: アプリ・DB・キャッシュを複数アベイラビリティゾーン（AZ）に分散する。MVP のコスト感に合わせ、まずは最小構成から始め冗長度を上げられる形にする。

---

## 3. 全体構成図（AWS / ターゲット構成）

```
                          ┌──────────────┐
        ユーザー ───────▶ │  Route 53    │  DNS
                          └──────┬───────┘
                                 │
                        ┌────────▼─────────┐
                        │       ALB        │  HTTPS/WSS 終端（ACM 証明書）
                        │ (Application LB) │  パスルーティングで同一オリジン配信
                        └────────┬─────────┘
              default（画面）     │      /api・/ws（REST / SockJS）
                 ┌───────────────┴───────────────┐
                 │                               │
   ┌─────────────▼──────────────┐   ┌────────────▼────────────────────┐
   │   VPC（private subnet）     │   │        VPC（private subnet）      │
   │  ┌────────────┐ ┌────────┐ │   │  ┌────────────┐  ┌────────────┐  │
   │  │ ECS Fargate│ │ECS Far.│ │   │  │ ECS Fargate│  │ ECS Fargate│  │  ← 複数タスク
   │  │ task (AZ-a)│ │(AZ-c)  │ │   │  │  task (AZ-a)│ │  task (AZ-c)│  │     Multi-AZ
   │  │  Next.js   │ │ Next.js│ │   │  │ Spring Boot │ │ Spring Boot │  │
   │  │ (standalone)│ │        │ │   │  └─────┬──────┘  └──────┬─────┘  │
   │  └────────────┘ └────────┘ │   │        │                │        │
   └────────────────────────────┘   └────────┼────────────────┼────────┘
                                              │                │
                  ┌──────────────────┬────────┼────────────────┼────────┬──────────────┐
                  │                  │        │                │        │              │
          ┌───────▼────────┐  ┌──────▼────────▼─┐   ┌──────────▼────────▼──┐   ┌───────▼───────┐
          │ ElastiCache    │  │ RDS for          │   │   S3（アバター／      │   │ CloudWatch    │
          │ for Redis      │  │ PostgreSQL       │   │   ファイル添付）      │   │ Logs/Metrics  │
          │ キャッシュ/    │  │ (Multi-AZ)       │   │   F-02 / F-10         │   │               │
          │ Pub-Sub        │  │ primary + standby│   │                       │   │               │
          └────────────────┘  └──────────────────┘   └───────────────────────┘   └───────────────┘
              (private)            (private)                                          (集約先)

   ※ シークレット（JWT_SECRET / DB / Redis / S3 認証情報）は SSM Parameter Store / Secrets Manager から注入（§10）
```

- 外部公開点は **ALB のみ**。フロント・バックエンド・DB・Redis はすべて private subnet に置く。
- ALB は **WSS（WebSocket over TLS）を終端**し、**パスルーティングで同一オリジン配信**する（§12.2 で確定）:
  - `default`（画面・静的アセット）→ **フロント ECS（Next.js standalone, port 3000）**
  - `/api`（REST）・`/ws`（STOMP over SockJS、[realtime-design.md](realtime-design.md)）→ **バックエンド ECS（Spring Boot, 8080）**
- 同一オリジンのためブラウザは相対 URL で REST/WS を叩け、**CORS を踏まない**。
- フロント・バックエンドそれぞれの ECS Fargate タスクを複数 AZ に分散して水平スケールし、タスク間のイベント配信は ElastiCache(Redis) Pub-Sub が橋渡しする（§6）。

---

## 4. 論理要素 → AWS サービス対応表

各論理要素を AWS サービスへマッピングする。ローカル（[`docker-compose.yml`](../docker-compose.yml)）との対応も併記する。

| 論理要素 | ローカル（dev）| 本番（AWS）| 備考 |
| --- | --- | --- | --- |
| フロント（Next.js）| `npm run dev`（port 3000）| ECS Fargate（standalone・複数タスク）| §12.2 で確定。単一 ALB の default ターゲット。イメージは ECR |
| バックエンド（Spring Boot）| ローカル JVM（port 8080）| ECS Fargate（コンテナ・複数タスク）| ステートレス。コンテナイメージは ECR。ALB の `/api`・`/ws` ルール先 |
| ロードバランサ / TLS 終端 | なし（直接アクセス）| ALB + ACM（証明書）| WSS / REST を終端し、パスでフロント / バックエンドへ振り分け |
| DNS | localhost | Route 53 | 独自ドメイン |
| キャッシュ / Pub-Sub | Redis（`redis:7-alpine`, 6379）| ElastiCache for Redis | キャッシュ（[cache-strategy.md](cache-strategy.md)）と Pub-Sub（[realtime-design.md](realtime-design.md)）兼用 |
| RDB | PostgreSQL（`postgres:17-alpine`, 5432）| RDS for PostgreSQL（Multi-AZ）| スキーマは Flyway で管理（[tech-stack.md](tech-stack.md)）|
| オブジェクトストレージ | LocalStack（`localstack/localstack:3`, 4566, `storage` profile）| S3 | アバター F-02・添付 F-10。エンドポイント差し替えで切替 |
| コンテナレジストリ | なし | ECR | ECS が pull するイメージ置き場 |
| シークレット / 設定 | `.env` / `application-dev.yml` | SSM Parameter Store / Secrets Manager | §10 |
| ログ / メトリクス | 標準出力 | CloudWatch Logs / Metrics | §9 |

> S3 はローカルでは LocalStack、本番では実 S3 を**エンドポイント差し替え**で利用する（[tech-stack.md §4](tech-stack.md)）。`application.yml` は endpoint / region / access-key を環境変数化済みのため、コード変更なしで切り替わる。

---

## 5. ネットワーク構成

### 5.1 VPC / サブネット

| 区分 | 配置するもの | 公開範囲 |
| --- | --- | --- |
| public subnet | ALB | インターネットから到達可 |
| private subnet | ECS Fargate タスク / RDS / ElastiCache | VPC 内のみ。インターネットから直接到達不可 |

- サブネットは **複数 AZ（最低 2 AZ）にまたがって**配置し、各層を AZ 分散できるようにする。
- private subnet から外部（ECR / S3 / CloudWatch 等）への通信は NAT Gateway もしくは VPC エンドポイントで通す（どちらにするかは §12・実装で確定）。

### 5.2 セキュリティグループ（通信経路）方針

最小権限で「直前の層からのみ受ける」よう絞る。

```
インターネット ──▶ ALB(SG)  : 443 のみ許可
ALB(SG) ─────────▶ ECS(SG)  : アプリポート(8080)を ALB SG からのみ許可
ECS(SG) ─────────▶ RDS(SG)  : 5432 を ECS SG からのみ許可
ECS(SG) ─────────▶ Redis(SG): 6379 を ECS SG からのみ許可
```

- RDS / ElastiCache は**インターネットに公開しない**。ECS のセキュリティグループからのみ受ける。
- 管理アクセス（DB への手動接続等）が必要な場合は bastion / Session Manager 経由とする（§8）。

---

## 6. 冗長化・可用性

冗長化の **論理設計は [リアルタイム通信設計書 §7・§10](realtime-design.md) を正** とし、本書はそれを支えるインフラ構成を定める。

### 6.1 アプリ層（ECS Fargate）

- バックエンドを **複数タスクに水平スケール**し、複数 AZ に分散する。
- 複数タスク時に「あるタスクが受けたメッセージを他タスクの接続クライアントへ届ける」問題は、**Redis Pub-Sub による橋渡し**で解決する（[realtime-design.md §7](realtime-design.md)）。各タスクが SUBSCRIBE し、自プロセスが持つ session に配信する。
- アプリは**ステートレス**（ファイルは S3、キャッシュ/Pub-Sub は Redis、認証は JWT）であるため、タスクの増減・入れ替えが安全に行える。
- WebSocket の特性上、ALB の **スティッキーセッション**を併用し、同一クライアントの接続を同一タスクに寄せる（再接続時の挙動は [realtime-design.md](realtime-design.md) の再接続戦略に従う）。

### 6.2 データ層

| コンポーネント | 冗長化方針 |
| --- | --- |
| RDS for PostgreSQL | Multi-AZ 配置（primary + standby、自動フェイルオーバー）|
| ElastiCache for Redis | レプリカ + 自動フェイルオーバー（MVP では単一ノードから開始し、必要に応じてレプリカ追加）|
| S3 | 標準で複数 AZ に冗長化済み（マネージド）|

### 6.3 単一障害点（SPOF）の整理

[要件定義書 §4](requirements.md) は可用性を「単一障害点とならない構成を**目標**（学習用途のためコストとのバランス）」と位置づける。本書もこれを踏襲し、**段階的に潰せる**形にする。

| 層 | SPOF リスク | 緩和策 | MVP での扱い |
| --- | --- | --- | --- |
| アプリ | 1 タスク障害で全断 | 複数タスク + Multi-AZ + ALB ヘルスチェック | 2 タスク以上を基本 |
| DB | 単一インスタンス障害 | RDS Multi-AZ | 採用 |
| キャッシュ/Pub-Sub | Redis 単一ノード障害で配信断 | ElastiCache レプリカ + フェイルオーバー | まず単一、後でレプリカ |
| LB | ALB 障害 | ALB 自体がマネージドで AZ 冗長 | マネージドに委ねる |
| ストレージ | — | S3 がマネージドで冗長 | マネージドに委ねる |

---

## 7. CI/CD（自動デプロイ）方針

[要件定義書 §10](requirements.md) フェーズ④（自動コードレビュー導入）・⑤（自動デプロイ）に対応する。**方向性のみ**定め、パイプライン定義（YAML）は実装フェーズで作成する。

### 7.1 デプロイの流れ（想定）

```
git push / PR merge (main)
   └─▶ GitHub Actions
         ├─ backend : Gradle build → テスト → Docker build → ECR push → ECS サービス更新
         └─ frontend: npm build → Docker build（standalone）→ ECR push → ECS サービス更新
```

- イメージは **ECR** に push し、ECS のサービス更新（ローリングデプロイ）でタスクを入れ替える。
- main への直接 push は禁止（[CLAUDE.md](../CLAUDE.md)）。デプロイの起点は **PR マージ後の main** とする。

> **実装（⑤Step6）**: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) で実装。GitHub→AWS は **OIDC** 認証（静的アクセスキー不要）で、デプロイロールは [`infra/terraform/modules/cicd`](../infra/terraform/modules/cicd) が main 限定・最小権限（ECR push / ECS UpdateService / 対象ロールへの PassRole）で発行する。イメージは commit SHA で固定タグ付けし、タスク定義を新リビジョン登録 → サービス更新。Flyway は起動時自動適用のため CD に専用ステップは設けない。

### 7.2 自動コードレビューとの関係

- フェーズ④で導入する `.github/workflows/claude-code-review.yml`（Claude Code 自動コードレビュー）は **PR 時に走る品質ゲート**。
- 本書のデプロイ用ワークフローは **マージ後（main）に走るデプロイゲート**。両者は別ワークフローとして共存させる。
- 両ワークフローとも実装済み（④: `claude-code-review.yml` / ⑤: `deploy.yml`）。共存させて役割を分離している。

---

## 8. 構成管理（Ansible）方針

上級編テーマの「Ansible によるプロビジョニング」をどこに適用するかを整理する。**適用範囲の方針のみ**定め、Playbook は実装フェーズで作成する。

### 8.1 「マネージド任せ」と「Ansible 管理」の切り分け

ECS Fargate は OS 層をユーザーが管理しないため、従来型の「サーバーに SSH して構成する」Ansible の出番は薄い。そこで対象を以下のように切り分ける。

| 対象 | 管理方法 |
| --- | --- |
| VPC / サブネット / SG / ALB / ECS / RDS / ElastiCache | IaC（CloudFormation / Terraform）でプロビジョニング |
| ミドルウェアの初期設定・運用タスク（DB 初期化、バッチ実行環境、bastion セットアップ等）| Ansible の題材として適する |
| 環境変数・シークレットの投入手順の自動化 | Ansible でも実行可能（§10 と連携）|

### 8.2 Ansible 題材を確保する選択肢

学習として Ansible を厚く扱いたい場合、**ECS Fargate ではなく EC2 起動型（ECS on EC2 / 素の EC2）** を一部採用すると、OS・ミドルウェアの構成管理という本来の Ansible 題材が生まれる。Fargate と EC2 のどちらを採るかは [§12 未決事項](#12-未決事項--今後検討) で扱う。

---

## 9. 監視・オブザーバビリティ方針

[要件定義書 §4](requirements.md) の「構造化ログを出力し後続で CloudWatch 等へ集約」に対応する。

| 観点 | 方針 |
| --- | --- |
| ログ | アプリは**構造化ログを標準出力**し、ECS から **CloudWatch Logs** に集約。[リアルタイム通信設計書 §10](realtime-design.md) の `sessionId` / `userId` / `destination` / `messageId` を全イベントログに含める方針と整合させる |
| メトリクス | CloudWatch Metrics で CPU / メモリ / タスク数 / ALB のリクエスト数・5xx・レイテンシ・WebSocket 接続数を監視 |
| ヘルスチェック | Spring Boot Actuator の `/actuator/health` を導入し、ALB のターゲットヘルスチェックに使う想定 |
| アラート | しきい値超過（5xx 増・タスク異常）で CloudWatch Alarm → 通知（具体的な通知先は運用フェーズで確定）|

> Actuator は現状の依存に未追加。導入時は `backend/build.gradle` に `spring-boot-starter-actuator` を加え、[tech-stack.md](tech-stack.md) の該当表も同 PR で更新する。

---

## 10. シークレット・設定管理

`application.yml` は **JWT_SECRET / DB 接続情報 / Redis / S3 認証情報を環境変数化済み**（[tech-stack.md](tech-stack.md)・[`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml)）。本番ではこれらを安全に注入する。

| 項目 | 保管先 | 注入方法 |
| --- | --- | --- |
| `JWT_SECRET` | Secrets Manager | ECS タスク定義の `secrets` で環境変数として注入 |
| DB ユーザー名 / パスワード | Secrets Manager（RDS と連携可）| 同上 |
| Redis 接続情報 | SSM Parameter Store | ECS タスク定義から参照 |
| S3 バケット名 / リージョン等の非機密設定 | SSM Parameter Store | ECS タスク定義の `environment` または `secrets` |
| S3 アクセス | **IAM ロール（タスクロール）**を優先 | アクセスキーを環境変数で持たず、ECS タスクロールで S3 権限を付与 |

- 機密値は**コード・リポジトリに置かない**。ローカルは `.env` / `application-dev.yml`、本番は上記マネージドサービスから注入する。
- S3 への認証は静的キーよりも **ECS タスクロール**（IAM）を優先し、鍵の配布・ローテーション負荷をなくす。

---

## 11. 環境構成（dev / 本番）

| 項目 | dev（ローカル）| 本番（AWS）|
| --- | --- | --- |
| 実行基盤 | Docker Compose / ローカル JVM・npm | ECS Fargate（フロント・バックエンドとも）＋ 単一 ALB |
| DB | `postgres:17-alpine`（compose）| RDS for PostgreSQL（Multi-AZ）|
| キャッシュ/Pub-Sub | `redis:7-alpine`（compose）| ElastiCache for Redis |
| オブジェクトストレージ | LocalStack（`storage` profile, 4566）| S3 |
| 設定/シークレット | `.env` / `application-dev.yml` | Secrets Manager / SSM |
| ログ | 標準出力 | CloudWatch Logs |
| プロファイル | `dev`（Flyway seed 投入）| 本番プロファイル（seed なし）|

- S3 は dev = LocalStack、本番 = 実 S3 を**エンドポイント差し替え**で切り替える（[tech-stack.md §4](tech-stack.md)）。
- DB スキーマは両環境とも **Flyway** で管理し、マイグレーションをデプロイに含める（[tech-stack.md](tech-stack.md)）。

---

## 12. 未決事項 / 今後検討

実装フェーズ（[要件定義書 §10](requirements.md) フェーズ⑤）で確定する。

### 12.1 確定済み（フェーズ⑤着手時に合意）

| 項目 | 決定 | 補足 |
| --- | --- | --- |
| IaC ツール | **Terraform** | コードは [`infra/terraform/`](../infra/terraform/README.md)。CloudFormation/CDK は不採用 |
| ECS 起動タイプ | **Fargate**（アプリ層）＋ **運用 EC2(bastion)** | bastion を Ansible のプロビジョニング題材にして上級編テーマを確保（§8.2）|
| Terraform state 管理 | **bootstrap モジュールで TF 管理**（S3 + DynamoDB ロック）| `infra/terraform/bootstrap/`。ほぼ無料 |
| apply 方針 | **author-only / オンデマンド** | 必要なファイルだけ作り、高額リソースは常駐させず必要時のみ apply→検証後 destroy。学習・CI は `terraform validate` まで無課金で回す |
| フロントの配信方式 | **ECS Fargate でホスト（Next.js standalone）＋ 単一 ALB パスルーティング**（Step5 で確定）| CloudFront + S3 静的書き出しは不採用。動的ルート（任意 ID の workspace/channel/dm）が `output: 'export'` では 404 になり、catch-all でも Next 標準遷移がフルリロード化するため、独自クライアントルーターを自作せず Next 標準ルーティングをそのまま使える ECS ホストを選択。`default`→フロント / `/api`・`/ws`→バックエンドで同一オリジン配信し CORS 不要。実装は [`modules/ecs`](../infra/terraform/modules/ecs/) に同居 |

### 12.2 引き続き未決

- **private → 外部通信**: NAT Gateway か VPC エンドポイントか（コストと到達先で判断）。
- **コスト試算**: MVP 規模（[§1.1](#11-想定スケールmvp)）での月額概算と、冗長度を上げた場合の差分。
- **大規模化時の broker 切替**: スケールが想定を大きく超えた場合の StompBrokerRelay + RabbitMQ 移行（[realtime-design.md §10.1](realtime-design.md)）。
