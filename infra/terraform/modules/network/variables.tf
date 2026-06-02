variable "name_prefix" {
  description = "リソース名の接頭辞（呼び出し元の local.name_prefix。例: raisechat-dev）"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC の CIDR ブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs_count" {
  description = "サブネットを分散させる AZ 数（infrastructure.md §5: 最低 2）"
  type        = number
  default     = 2

  validation {
    condition     = var.azs_count >= 2
    error_message = "azs_count は単一障害点を避けるため 2 以上にする（infrastructure.md §5）。"
  }
}

variable "enable_nat_gateway" {
  description = <<-EOT
    private subnet から外部（ECR/CloudWatch 等）への egress 用に単一 NAT Gateway を作るか。
    author-only 方針のため既定は false（validate 中は不要・無課金）。
    将来の E2E デプロイ検証時に true にして NAT 経由 egress を有効化する。
  EOT
  type        = bool
  default     = false
}
