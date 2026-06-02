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
