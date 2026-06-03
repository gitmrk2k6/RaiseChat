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

# storage: アプリ用 S3 バケット（アバター F-02 / 添付 F-10）。
# バケット本体を Terraform 管理化し、ecs に bucket_name / public_base_url を配線する。
# force_destroy=true なので destroy でオブジェクトごと消え、全クリーンアップを保証する。
module "storage" {
  source      = "../../modules/storage"
  name_prefix = local.name_prefix
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
  cpu_architecture   = var.cpu_architecture
  container_image    = var.container_image
  desired_count      = var.ecs_desired_count
  task_cpu           = var.ecs_task_cpu
  task_memory        = var.ecs_task_memory
  s3_bucket          = module.storage.bucket_name
  s3_public_base_url = module.storage.public_base_url
  ws_allowed_origins = var.ws_allowed_origins
  invite_base_url    = var.invite_base_url

  # フロント（同一 ALB の default ターゲット。/api・/ws のみ backend へ振る）。
  frontend_container_image = var.frontend_container_image
  frontend_desired_count   = var.frontend_desired_count
  frontend_task_cpu        = var.frontend_task_cpu
  frontend_task_memory     = var.frontend_task_memory
}

# Step 6: CI/CD（GitHub Actions OIDC デプロイ）。
# GitHub→AWS を OIDC で接続し、main 限定のデプロイロールを発行する。権限は ecs module の
# output（ECR / ECS サービス / IAM ロール ARN）で「この環境のリソースだけ」に絞る。
# author-only（apply は ecs と同時）。ワークフロー本体は .github/workflows/deploy.yml。
module "cicd" {
  source      = "../../modules/cicd"
  name_prefix = local.name_prefix

  github_owner  = var.github_owner
  github_repo   = var.github_repo
  github_branch = var.github_deploy_branch

  ecr_repository_arns = [
    module.ecs.backend_ecr_repository_arn,
    module.ecs.frontend_ecr_repository_arn,
  ]
  ecs_service_arns = [
    module.ecs.ecs_service_arn,
    module.ecs.frontend_ecs_service_arn,
  ]
  ecs_pass_role_arns = [
    module.ecs.ecs_execution_role_arn,
    module.ecs.ecs_task_role_arn,
  ]
}

# Step 8: 運用 bastion（Ansible プロビジョニングの題材 ＋ RDS/Redis への踏み台）。
# network（VPC / public subnet / RDS・Redis SG）と data（DB/Redis ポート）を配線する。
# 接続は SSM Session Manager（インバウンド SSH なし）。author-only（apply は運用時のみ）。
module "bastion" {
  source      = "../../modules/bastion"
  name_prefix = local.name_prefix

  vpc_id           = module.network.vpc_id
  public_subnet_id = module.network.public_subnet_ids[0]
  rds_sg_id        = module.network.rds_sg_id
  redis_sg_id      = module.network.redis_sg_id

  db_port    = module.data.db_instance_port
  redis_port = module.data.redis_port
}

# Step 7: 監視（CloudWatch アラーム / ダッシュボード / SNS 通知トピック）。
# ecs module の output（クラスタ名・サービス名・ALB/TG の ARN suffix）と desired count を
# 配線する。SNS の購読は apply 後に手動追加（infrastructure.md §9）。author-only。
module "monitoring" {
  source      = "../../modules/monitoring"
  name_prefix = local.name_prefix
  aws_region  = var.aws_region

  cluster_name          = module.ecs.ecs_cluster_name
  backend_service_name  = module.ecs.ecs_service_name
  frontend_service_name = module.ecs.frontend_ecs_service_name

  backend_desired_count  = var.ecs_desired_count
  frontend_desired_count = var.frontend_desired_count

  alb_arn_suffix                   = module.ecs.alb_arn_suffix
  backend_target_group_arn_suffix  = module.ecs.backend_target_group_arn_suffix
  frontend_target_group_arn_suffix = module.ecs.frontend_target_group_arn_suffix
}
