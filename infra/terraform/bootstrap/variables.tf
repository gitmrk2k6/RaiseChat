variable "project_name" {
  description = "リソース名・タグの接頭辞に使うプロジェクト名"
  type        = string
  default     = "raisechat"
}

variable "aws_region" {
  description = "state backend を作成するリージョン（envs と揃える）"
  type        = string
  default     = "ap-northeast-1"
}

variable "state_bucket_name" {
  description = "Terraform state を保存する S3 バケット名（グローバルで一意にする必要がある）"
  type        = string
  default     = "raisechat-tfstate"
}

variable "lock_table_name" {
  description = "Terraform state ロック用の DynamoDB テーブル名"
  type        = string
  default     = "raisechat-tflock"
}
