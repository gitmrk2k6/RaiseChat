# ===========================================================================
# dev 環境ルートスタック
#
# このファイルが dev 環境の組み立て地点。⑤ロードマップの各ステップで作る
# module をここから呼び出して構成を積み上げていく。
#
# 想定される追加順（infra/terraform/README.md・docs/infrastructure.md 参照）:
#   Step 2  module "network"  { source = "../../modules/network"  ... }  # VPC/Subnet/SG ← 実装済
#   Step 3  module "data"     { source = "../../modules/data"     ... }  # RDS/ElastiCache
#   Step 4  module "ecs"      { source = "../../modules/ecs"      ... }  # ECR/Fargate/ALB（backend）
#   Step 5  フロントは ecs module に同居（ECS Fargate ホスト・単一 ALB パスルーティング）。
#           §12.2 で当初の CloudFront/S3 案ではなく ECS ホストに確定したため modules/frontend は作らない。
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

# Step 3: データ層（RDS for PostgreSQL / ElastiCache for Redis）。
# network の出力（private subnet / RDS・Redis SG）を受けて private に配置する。
# 冗長度（Multi-AZ / レプリカ）は既定で最小。E2E 検証時に tfvars で引き上げる。
module "data" {
  source             = "../../modules/data"
  name_prefix        = local.name_prefix
  private_subnet_ids = module.network.private_subnet_ids
  rds_sg_id          = module.network.rds_sg_id
  redis_sg_id        = module.network.redis_sg_id

  db_instance_class        = var.db_instance_class
  db_multi_az              = var.db_multi_az
  redis_node_type          = var.redis_node_type
  redis_num_cache_clusters = var.redis_num_cache_clusters
}

# Step 4: アプリ実行基盤（ECR / ALB / ECS Fargate / CloudWatch Logs / IAM / シークレット）。
# network（public/private subnet・alb_sg・ecs_sg）と data（DB シークレット・Redis）の出力を配線する。
# ALB の TLS は certificate_arn のトグル（未指定なら HTTP:80 フォールバック）。author-only（apply は E2E 時のみ）。
module "ecs" {
  source      = "../../modules/ecs"
  name_prefix = local.name_prefix

  vpc_id             = module.network.vpc_id
  public_subnet_ids  = module.network.public_subnet_ids
  private_subnet_ids = module.network.private_subnet_ids
  alb_sg_id          = module.network.alb_sg_id
  ecs_sg_id          = module.network.ecs_sg_id

  db_endpoint_host          = module.data.db_instance_address
  db_port                   = module.data.db_instance_port
  db_name                   = module.data.db_name
  db_master_user_secret_arn = module.data.db_master_user_secret_arn
  redis_host                = module.data.redis_primary_endpoint_address
  redis_port                = module.data.redis_port

  certificate_arn    = var.certificate_arn
  container_image    = var.container_image
  desired_count      = var.ecs_desired_count
  task_cpu           = var.ecs_task_cpu
  task_memory        = var.ecs_task_memory
  s3_bucket          = var.s3_bucket
  ws_allowed_origins = var.ws_allowed_origins
  invite_base_url    = var.invite_base_url

  # フロント（同一 ALB の default ターゲット。/api・/ws のみ backend へ振る）。
  frontend_container_image = var.frontend_container_image
  frontend_desired_count   = var.frontend_desired_count
  frontend_task_cpu        = var.frontend_task_cpu
  frontend_task_memory     = var.frontend_task_memory
}
