# bootstrap は「リモート state 置き場」自体を作るスタックなので、
# ここだけは local state（このディレクトリ内の terraform.tfstate）で管理する。
# 1 回だけ apply すれば、以降は envs/* がこの S3 / DynamoDB を backend として使う。
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
