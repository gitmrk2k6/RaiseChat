variable "name_prefix" {
  description = "リソース名の接頭辞（呼び出し元の local.name_prefix。例: raisechat-dev）"
  type        = string
}

# --- network module から受け取る配線情報 -----------------------------------
variable "private_subnet_ids" {
  description = "RDS / ElastiCache を置く private subnet の ID 一覧（network module の出力）。Multi-AZ 化に備え 2 つ以上を渡す"
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "private_subnet_ids は Multi-AZ 配置に備え 2 つ以上にする（infrastructure.md §5・§6.2）。"
  }
}

variable "rds_sg_id" {
  description = "RDS にアタッチするセキュリティグループ ID（network module の rds_sg_id。ECS SG からのみ 5432 を受ける）"
  type        = string
}

variable "redis_sg_id" {
  description = "ElastiCache にアタッチするセキュリティグループ ID（network module の redis_sg_id。ECS SG からのみ 6379 を受ける）"
  type        = string
}

# --- RDS for PostgreSQL ----------------------------------------------------
variable "db_engine_version" {
  description = "PostgreSQL のエンジンバージョン。ローカル（postgres:17-alpine）と合わせ 17 系にする"
  type        = string
  default     = "17.4"
}

variable "db_instance_class" {
  description = "RDS インスタンスクラス。MVP/dev は burstable な t4g 系で最小から"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "初期割当ストレージ（GiB）"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "ストレージ自動スケールの上限（GiB）。db_allocated_storage と同値にすると自動スケール無効"
  type        = number
  default     = 100
}

variable "db_name" {
  description = "初期作成するデータベース名"
  type        = string
  default     = "raisechat"
}

variable "db_username" {
  description = "マスターユーザー名。パスワードは manage_master_user_password により Secrets Manager で自動管理する"
  type        = string
  default     = "raisechat"
}

variable "db_multi_az" {
  description = <<-EOT
    RDS を Multi-AZ（primary + standby）にするか。
    author-only 方針のため既定は false（standby 分の課金を避ける）。
    設計の Multi-AZ 目標（infrastructure.md §6.2）は E2E 検証時に true で担保する。
  EOT
  type        = bool
  default     = false
}

variable "db_backup_retention_period" {
  description = "自動バックアップの保持日数。0 で無効。dev は最小、本番は要見直し"
  type        = number
  default     = 1
}

variable "db_deletion_protection" {
  description = "削除保護。author-only で destroy を回す dev は false、本番相当では true を検討"
  type        = bool
  default     = false
}

variable "db_skip_final_snapshot" {
  description = "destroy 時に最終スナップショットを取らないか。on-demand な dev は true（検証後すぐ destroy する想定）"
  type        = bool
  default     = true
}

# --- ElastiCache for Redis -------------------------------------------------
variable "redis_engine_version" {
  description = "Redis のエンジンバージョン。ローカル（redis:7-alpine）と合わせ 7 系にする"
  type        = string
  default     = "7.1"
}

variable "redis_node_type" {
  description = "ElastiCache ノードタイプ。MVP/dev は t4g 系で最小から"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_num_cache_clusters" {
  description = <<-EOT
    レプリケーショングループのノード数。1 で単一ノード（infrastructure.md §6.2 の初期方針）。
    2 以上にすると自動フェイルオーバーと Multi-AZ を自動で有効化する。
  EOT
  type        = number
  default     = 1

  validation {
    condition     = var.redis_num_cache_clusters >= 1
    error_message = "redis_num_cache_clusters は 1 以上にする。"
  }
}
