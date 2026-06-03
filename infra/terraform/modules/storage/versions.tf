# module 単体でも整合を取れるようバージョンを宣言する。provider ブロックは置かず、
# 呼び出し元（envs/<env>）の provider（region・default_tags）を継承する。
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
