provider "aws" {
  region = var.aws_region

  # 全リソースに共通タグを自動付与する。個別リソース側で tags を書かなくても付く。
  default_tags {
    tags = local.common_tags
  }
}
