# modules/cicd

RaiseChat の自動デプロイ基盤（⑤Step6）。**GitHub Actions が OIDC で AWS に assume する
デプロイロール**と、その信頼関係（**OIDC provider**）を IaC 化する。長期 IAM ユーザー
（アクセスキー）を発行せず、特定リポジトリ・特定ブランチからの GitHub Actions のみを
信頼する構成。設計の正は [docs/infrastructure.md §7](../../../../docs/infrastructure.md)。

実際のデプロイ手順（ECR push → ECS UpdateService）は
[`.github/workflows/deploy.yml`](../../../../.github/workflows/deploy.yml) 側に置く。
本モジュールは **AWS 側の信頼設定と最小権限の付与** を担う。

## 責務

- **OIDC provider** (`token.actions.githubusercontent.com`): GitHub Actions の
  OIDC トークンを信頼する。既存 provider を再利用する場合は `create_oidc_provider=false`
  にして `existing_oidc_provider_arn` を渡す（1 アカウントに 1 つで足りるため）
- **デプロイロール** (`<prefix>-...-deploy`): 信頼ポリシーの `sub` 条件で
  `github_owner` / `github_repo` / `github_branch`（既定 `main`）を満たす
  GitHub Actions のみが assume できる
- **最小権限ポリシー**: ECR への push、対象 ECS サービスの `UpdateService`、
  タスク定義登録時の `PassRole`（execution / task ロール）に限定

## ecs module / envs との配線

デプロイ対象（ECR / ECS サービス / 各ロール）は `modules/ecs` の output を配線する。

```
ecs.backend_ecr_repository_arn / frontend_ecr_repository_arn ─▶ ecr_repository_arns
ecs.ecs_service_arn / frontend_ecs_service_arn               ─▶ ecs_service_arns
ecs.ecs_execution_role_arn / ecs_task_role_arn               ─▶ ecs_pass_role_arns
```

## 入力（主なもの）

| 変数 | 既定 | 用途 |
| --- | --- | --- |
| `github_owner` | — | 信頼ポリシーの `sub` 条件（org / user）|
| `github_repo` | — | 信頼ポリシーの `sub` 条件（リポジトリ名）|
| `github_branch` | `main` | デプロイを許可するブランチ（CD 起点）|
| `create_oidc_provider` | — | OIDC provider を本 module で作成するか |
| `existing_oidc_provider_arn` | — | 再利用する既存 provider の ARN |
| `ecr_repository_arns` | — | push を許可する ECR リポジトリ ARN |
| `ecs_service_arns` | — | `UpdateService` を許可する ECS サービス ARN |
| `ecs_pass_role_arns` | — | `PassRole` を許可する IAM ロール ARN |

## 出力

| 出力 | 用途 |
| --- | --- |
| `deploy_role_arn` | workflow の `role-to-assume` に設定するデプロイロール ARN |
| `oidc_provider_arn` | 作成 / 再利用した OIDC provider の ARN |

## apply 方針

author-only / オンデマンド。本モジュール追加時点では **apply せず** `terraform fmt /
validate / tflint` まで（無課金）。apply 後、`deploy_role_arn` を GitHub Actions の
`role-to-assume` に設定するとデプロイが通る。
