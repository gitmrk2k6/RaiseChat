variable "project_name" {
  description = "リソース名・タグの接頭辞に使うプロジェクト名"
  type        = string
  default     = "raisechat"
}

variable "environment" {
  description = "環境名（dev / prod など）。name_prefix と Environment タグに使う"
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "リソースを作成する AWS リージョン"
  type        = string
  default     = "ap-northeast-1"
}

# --- network module 用 ------------------------------------------------------
variable "vpc_cidr" {
  description = "VPC の CIDR ブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs_count" {
  description = "サブネットを分散させる AZ 数（最低 2）"
  type        = number
  default     = 2
}

variable "enable_nat_gateway" {
  description = "private egress 用の単一 NAT Gateway を作るか。author-only のため既定 false（E2E 検証時のみ true）"
  type        = bool
  default     = false
}

# --- data module 用 ---------------------------------------------------------
# 冗長度・サイジングの主要ノブだけを env 側に露出する。その他は module 既定に委ねる。
variable "db_instance_class" {
  description = "RDS インスタンスクラス。MVP/dev は t4g 系で最小から"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_multi_az" {
  description = "RDS を Multi-AZ にするか。author-only のため既定 false（E2E 検証時のみ true）"
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache ノードタイプ。MVP/dev は t4g 系で最小から"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_num_cache_clusters" {
  description = "Redis レプリケーショングループのノード数。1 で単一、2 以上で自動フェイルオーバー＋Multi-AZ"
  type        = number
  default     = 1
}

# --- ecs module 用 ----------------------------------------------------------
variable "certificate_arn" {
  description = "ALB の HTTPS:443 に使う ACM 証明書 ARN。空なら HTTP:80 フォールバック（ドメイン未整備でも E2E 可）"
  type        = string
  default     = ""
}

variable "container_image" {
  description = "ECS タスクのコンテナイメージ。空なら本スタックの ECR の :latest を使う（push 後に apply）"
  type        = string
  default     = ""
}

variable "ecs_desired_count" {
  description = "ECS サービスの希望タスク数。§6.1「2 タスク以上」に従い既定 2"
  type        = number
  default     = 2
}

variable "ecs_task_cpu" {
  description = "ECS タスクの CPU ユニット"
  type        = number
  default     = 512
}

variable "ecs_task_memory" {
  description = "ECS タスクのメモリ（MiB）"
  type        = number
  default     = 1024
}

# --- フロントエンド（ECS ホスト・単一 ALB パスルーティング / Step5）---------
variable "frontend_container_image" {
  description = "フロント ECS タスクのコンテナイメージ。空なら本スタックの frontend ECR の :latest を使う（push 後に apply）"
  type        = string
  default     = ""
}

variable "frontend_desired_count" {
  description = "フロント ECS サービスの希望タスク数。§6.1「2 タスク以上」に従い既定 2"
  type        = number
  default     = 2
}

variable "frontend_task_cpu" {
  description = "フロント ECS タスクの CPU ユニット。SSR は軽いので既定 256"
  type        = number
  default     = 256
}

variable "frontend_task_memory" {
  description = "フロント ECS タスクのメモリ（MiB）。既定 512"
  type        = number
  default     = 512
}

# S3 バケット名・公開 URL は modules/storage が所有し ecs に配線するため、ここでは変数を持たない。

variable "cpu_architecture" {
  description = <<-EOT
    Fargate の CPU アーキテクチャ（X86_64 / ARM64）。push するイメージのビルド先と一致させる。
    Apple Silicon でネイティブビルドして push するため既定 ARM64（exec format error 回避・Graviton で安価）。
  EOT
  type        = string
  default     = "ARM64"
}

variable "ws_allowed_origins" {
  description = "WS/REST CORS の許可オリジン（WS_ALLOWED_ORIGINS / APP_CORS_ALLOWED_ORIGINS）。空ならアプリ既定"
  type        = string
  default     = ""
}

variable "invite_base_url" {
  description = "招待リンクのベース URL（INVITE_BASE_URL）。空ならアプリ既定"
  type        = string
  default     = ""
}

# --- cicd module 用（⑤Step6 / GitHub Actions OIDC デプロイ）-----------------
variable "github_owner" {
  description = "GitHub オーナー（org / user）。デプロイロールの信頼ポリシー sub 条件に使う"
  type        = string
  default     = "gitmrk2k6"
}

variable "github_repo" {
  description = "GitHub リポジトリ名。デプロイロールの信頼ポリシー sub 条件に使う"
  type        = string
  default     = "RaiseChat"
}

variable "github_deploy_branch" {
  description = "デプロイを許可するブランチ。main マージ後の CD 起点（CLAUDE.md）"
  type        = string
  default     = "main"
}
