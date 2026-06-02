locals {
  # 以降のステップで作る全リソースの命名はこの接頭辞を起点にする（例: raisechat-dev-vpc）。
  # 命名の単一ソースにして、環境ごとの衝突を防ぐ。
  name_prefix = "${var.project_name}-${var.environment}"

  # provider の default_tags に渡す共通タグ。infrastructure.md §10 のタグ方針と整合させる。
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
