provider "aws" {
  region = var.aws_region

  # 全リソースに共通タグを自動付与する（手動付与漏れを防ぐ）。
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = "shared"
      ManagedBy   = "Terraform"
      Component   = "tf-state-backend"
    }
  }
}
