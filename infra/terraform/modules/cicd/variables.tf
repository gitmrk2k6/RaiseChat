variable "name_prefix" {
  description = "リソース命名の接頭辞（例: raisechat-dev）。呼び出し元の local.name_prefix を渡す"
  type        = string
}

# --- GitHub OIDC 信頼条件 ----------------------------------------------------
variable "github_owner" {
  description = "GitHub のオーナー（org / user）。信頼ポリシーの sub 条件に使う"
  type        = string
}

variable "github_repo" {
  description = "GitHub リポジトリ名。信頼ポリシーの sub 条件に使う"
  type        = string
}

variable "github_branch" {
  description = "デプロイを許可するブランチ。main マージ後の CD 起点（CLAUDE.md）"
  type        = string
  default     = "main"
}

# 同一 AWS アカウントに GitHub OIDC provider が既にある場合は false にし、
# existing_oidc_provider_arn に既存 ARN を渡して再利用する（provider はアカウント単位で一意）。
variable "create_oidc_provider" {
  description = "GitHub Actions 用 OIDC provider を本 module で作成するか"
  type        = bool
  default     = true
}

variable "existing_oidc_provider_arn" {
  description = "create_oidc_provider=false のとき再利用する既存 OIDC provider の ARN"
  type        = string
  default     = ""
}

# --- デプロイロールの権限スコープ（最小権限。ecs module の output を配線）-----
variable "ecr_repository_arns" {
  description = "CD が push を許可される ECR リポジトリ ARN の一覧（backend / frontend）"
  type        = list(string)
}

variable "ecs_service_arns" {
  description = "CD が UpdateService を許可される ECS サービス ARN の一覧（backend / frontend）"
  type        = list(string)
}

variable "ecs_pass_role_arns" {
  description = "タスク定義登録時に PassRole を許可する IAM ロール ARN（execution / task ロール）"
  type        = list(string)
}
