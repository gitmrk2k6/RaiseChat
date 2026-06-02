# Step 2 以降で module を追加するたびに、必要な出力（VPC ID / ALB DNS 名など）を
# ここに追記する。

output "name_prefix" {
  description = "この環境のリソース命名接頭辞（以降の module 名付けの起点）"
  value       = local.name_prefix
}

# --- network module（Step 2）-----------------------------------------------
output "vpc_id" {
  description = "VPC ID"
  value       = module.network.vpc_id
}

output "public_subnet_ids" {
  description = "public subnet ID 一覧（ALB 配置用）"
  value       = module.network.public_subnet_ids
}

output "private_subnet_ids" {
  description = "private subnet ID 一覧（ECS/RDS/Redis 配置用）"
  value       = module.network.private_subnet_ids
}

output "alb_sg_id" {
  description = "ALB 用 SG ID"
  value       = module.network.alb_sg_id
}

output "ecs_sg_id" {
  description = "ECS 用 SG ID"
  value       = module.network.ecs_sg_id
}

output "rds_sg_id" {
  description = "RDS 用 SG ID"
  value       = module.network.rds_sg_id
}

output "redis_sg_id" {
  description = "Redis 用 SG ID"
  value       = module.network.redis_sg_id
}

# --- data module（Step 3）---------------------------------------------------
output "db_instance_endpoint" {
  description = "RDS エンドポイント（host:port）"
  value       = module.data.db_instance_endpoint
}

output "db_instance_port" {
  description = "RDS ポート"
  value       = module.data.db_instance_port
}

output "db_name" {
  description = "初期 DB 名"
  value       = module.data.db_name
}

output "db_master_user_secret_arn" {
  description = "RDS マスターパスワードの Secrets Manager ARN（ECS タスク定義から参照）"
  value       = module.data.db_master_user_secret_arn
}

output "redis_primary_endpoint_address" {
  description = "Redis プライマリエンドポイント"
  value       = module.data.redis_primary_endpoint_address
}

output "redis_port" {
  description = "Redis ポート"
  value       = module.data.redis_port
}

# --- ecs module（Step 4 / Step 5）------------------------------------------
output "ecr_repository_url" {
  description = "バックエンドの ECR リポジトリ URL（CI が docker push する先）"
  value       = module.ecs.ecr_repository_url
}

output "frontend_ecr_repository_url" {
  description = "フロントエンドの ECR リポジトリ URL（CI が docker push する先 / Step5）"
  value       = module.ecs.frontend_ecr_repository_url
}

output "alb_dns_name" {
  description = "ALB の DNS 名（E2E の接続先 / Route 53 alias 先）。default はフロント、/api・/ws は backend"
  value       = module.ecs.alb_dns_name
}

output "ecs_cluster_name" {
  description = "ECS クラスタ名（CD のサービス更新で指定）"
  value       = module.ecs.ecs_cluster_name
}

output "ecs_service_name" {
  description = "バックエンド ECS サービス名（CD のサービス更新で指定）"
  value       = module.ecs.ecs_service_name
}

output "frontend_ecs_service_name" {
  description = "フロント ECS サービス名（CD のサービス更新で指定 / Step5）"
  value       = module.ecs.frontend_ecs_service_name
}

output "log_group_name" {
  description = "アプリログの CloudWatch Logs グループ名"
  value       = module.ecs.log_group_name
}
