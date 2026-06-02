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
