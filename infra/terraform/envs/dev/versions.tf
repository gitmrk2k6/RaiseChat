terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    # ecs module が JWT_SECRET の値生成に使う（§10。値をリポジトリに残さない）。
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}
