# --- ECR --------------------------------------------------------------------
output "ecr_repository_url" {
  description = "バックエンドの ECR リポジトリ URL（CI が docker push する先）"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_repository_name" {
  description = "ECR リポジトリ名"
  value       = aws_ecr_repository.backend.name
}

# --- ALB --------------------------------------------------------------------
output "alb_dns_name" {
  description = "ALB の DNS 名（Route 53 の別名レコード先・E2E の接続先）"
  value       = aws_lb.this.dns_name
}

output "alb_zone_id" {
  description = "ALB の Hosted Zone ID（Route 53 alias レコード作成用）"
  value       = aws_lb.this.zone_id
}

output "alb_arn" {
  description = "ALB の ARN"
  value       = aws_lb.this.arn
}

# --- ECS --------------------------------------------------------------------
output "ecs_cluster_name" {
  description = "ECS クラスタ名（CD のサービス更新で指定する）"
  value       = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  description = "ECS サービス名（CD のサービス更新で指定する）"
  value       = aws_ecs_service.backend.name
}

output "task_definition_arn" {
  description = "タスク定義の ARN（family:revision）"
  value       = aws_ecs_task_definition.backend.arn
}

output "log_group_name" {
  description = "アプリログの CloudWatch Logs グループ名（§9）"
  value       = aws_cloudwatch_log_group.backend.name
}

# --- シークレット -----------------------------------------------------------
output "jwt_secret_arn" {
  description = "JWT_SECRET を保管する Secrets Manager シークレットの ARN（§10）"
  value       = aws_secretsmanager_secret.jwt.arn
}
