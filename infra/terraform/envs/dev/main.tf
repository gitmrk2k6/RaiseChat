# ===========================================================================
# dev 環境ルートスタック
#
# このファイルが dev 環境の組み立て地点。⑤ロードマップの各ステップで作る
# module をここから呼び出して構成を積み上げていく。
#
# 想定される追加順（infra/terraform/README.md・docs/infrastructure.md 参照）:
#   Step 2  module "network"  { source = "../../modules/network"  ... }  # VPC/Subnet/SG ← 実装済
#   Step 3  module "data"     { source = "../../modules/data"     ... }  # RDS/ElastiCache
#   Step 4  module "ecs"      { source = "../../modules/ecs"      ... }  # ECR/Fargate/ALB
#   Step 5  module "frontend" { source = "../../modules/frontend" ... }  # CloudFront/S3
#   各 module には共通の入力として local.name_prefix / var.aws_region を渡す。
# ===========================================================================

# Step 2: ネットワーク基盤（VPC / 複数AZ subnet / SG 経路）。
# 共通タグは provider の default_tags が module 内リソースにも自動付与する。
module "network" {
  source             = "../../modules/network"
  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  azs_count          = var.azs_count
  enable_nat_gateway = var.enable_nat_gateway
}
