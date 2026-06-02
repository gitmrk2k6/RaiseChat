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
