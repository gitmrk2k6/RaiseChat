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
