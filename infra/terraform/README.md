# RaiseChat インフラ（Terraform）

RaiseChat の AWS インフラを Terraform（IaC）で管理する。設計の背景・全体構成は
[docs/infrastructure.md](../../docs/infrastructure.md) を正とする。本 README は
**コードの構成・運用手順・課金スタンス**を定める。

## 方針（重要）

- **author-only / オンデマンド apply**: 必要なファイルだけ作り、`apply` は必要なときだけ行う。
  NAT Gateway・RDS Multi-AZ・ElastiCache・ALB・Fargate を常時起動すると月額が嵩むため、
  **常駐させない**。学習・検証は `terraform validate` までをローカルで無課金で回す。
- **state backend は Terraform 自身で管理**（`bootstrap/`）。これ自体は S3 + DynamoDB のみで
  保存量・リクエストともごく僅かなのでほぼ無料。

## ディレクトリ構成

```
infra/terraform/
  bootstrap/     リモート state 置き場（S3 + DynamoDB）を作る。local state。1 回だけ apply。
  envs/
    dev/         dev 環境のルートスタック。modules を呼び出して構成を組み立てる。
  modules/       再利用モジュール（network / data / ecs / cicd / monitoring / bastion）。
                 ※ フロントは ECS ホスト確定のため ecs モジュールに同居（単一 ALB パスルーティング・§12.2）。専用 frontend モジュールは作らない。
```

## 命名・タグ規約

- リソース名は `envs/<env>/locals.tf` の `name_prefix`（例 `raisechat-dev`）を起点にする。
- 共通タグ（`Project` / `Environment` / `ManagedBy=Terraform`）は provider の `default_tags` で
  自動付与する。個別リソースに毎回書かない。
- provider / Terraform 本体のバージョンは各スタックの `versions.tf` でピン留め。
  `.terraform.lock.hcl` は**コミットする**（プロバイダ版の再現性のため）。

## 使い方

### 1. 無課金で検証する（通常の開発・CI）

backend（S3/DynamoDB）が未作成でも通る。実 AWS 認証情報も不要。

```bash
# 整形チェック & 静的解析
terraform fmt -check -recursive infra/terraform
tflint --chdir=infra/terraform/envs/dev
tflint --chdir=infra/terraform/bootstrap

# 構文・型検査（-backend=false で backend を無視）
cd infra/terraform/envs/dev && terraform init -backend=false && terraform validate
cd infra/terraform/bootstrap && terraform init -backend=false && terraform validate
```

### 2. 実際に state backend を使い始める（任意・初回のみ）

```bash
cd infra/terraform/bootstrap
terraform init          # local state で初期化
terraform apply         # S3 バケット + DynamoDB ロックテーブルを作成（ほぼ無料）
terraform output        # bucket / table 名を確認（envs/dev/backend.tf の値と一致させる）
```

その後、dev 環境を backend 付きで初期化する。

```bash
cd ../envs/dev
terraform init          # S3 backend へ初期化（以降 state はリモート管理）
```

### 3. 実リソースを作る／壊す（必要なときだけ）

```bash
cd infra/terraform/envs/dev
terraform plan -var-file=dev.tfvars     # 差分確認
terraform apply -var-file=dev.tfvars    # 作成（課金が発生）
terraform destroy -var-file=dev.tfvars  # 検証が済んだら必ず破棄（課金を止める）
```

## スコープ外（別ステップで追加）

- **GitHub→AWS OIDC プロバイダ / デプロイ用 IAM ロール**: ⑤Step 6（デプロイ用 GitHub Actions）で追加する。
  本スタックには含めない（apply 必須になるため）。
- アプリの Dockerfile / ECR への push: Step 4 で扱う。
