output "vpc_id" {
  description = "作成した VPC の ID"
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "VPC の CIDR ブロック"
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "public subnet の ID 一覧（ALB 配置用）"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "private subnet の ID 一覧（ECS/RDS/Redis 配置用）"
  value       = aws_subnet.private[*].id
}

output "alb_sg_id" {
  description = "ALB 用セキュリティグループ ID"
  value       = aws_security_group.alb.id
}

output "ecs_sg_id" {
  description = "ECS タスク用セキュリティグループ ID"
  value       = aws_security_group.ecs.id
}

output "rds_sg_id" {
  description = "RDS 用セキュリティグループ ID"
  value       = aws_security_group.rds.id
}

output "redis_sg_id" {
  description = "ElastiCache(Redis) 用セキュリティグループ ID"
  value       = aws_security_group.redis.id
}
