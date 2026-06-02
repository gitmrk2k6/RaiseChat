# --- ECR --------------------------------------------------------------------
output "ecr_repository_url" {
  description = "バックエンドの ECR リポジトリ URL（CI が docker push する先）"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_repository_name" {
  description = "ECR リポジトリ名"
  value       = aws_ecr_repository.backend.name
}

output "frontend_ecr_repository_url" {
  description = "フロントエンドの ECR リポジトリ URL（CI が docker push する先）"
  value       = aws_ecr_repository.frontend.repository_url
}

output "frontend_ecr_repository_name" {
  description = "フロントエンドの ECR リポジトリ名"
  value       = aws_ecr_repository.frontend.name
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
  description = "バックエンド ECS サービス名（CD のサービス更新で指定する）"
  value       = aws_ecs_service.backend.name
}

output "frontend_ecs_service_name" {
  description = "フロント ECS サービス名（CD のサービス更新で指定する）"
  value       = aws_ecs_service.frontend.name
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

# --- CD（cicd module）が最小権限で参照する ARN 群（⑤Step6）-----------------
# GitHub Actions のデプロイロールを「この環境のリソースだけ」に絞るため、
# ECR / ECS / IAM ロールの ARN を出力して cicd module に配線する。
output "backend_ecr_repository_arn" {
  description = "バックエンド ECR リポジトリの ARN（CD の push 権限スコープ用）"
  value       = aws_ecr_repository.backend.arn
}

output "frontend_ecr_repository_arn" {
  description = "フロント ECR リポジトリの ARN（CD の push 権限スコープ用）"
  value       = aws_ecr_repository.frontend.arn
}

output "ecs_cluster_arn" {
  description = "ECS クラスタの ARN"
  value       = aws_ecs_cluster.this.arn
}

output "ecs_service_arn" {
  description = "バックエンド ECS サービスの ARN（CD の UpdateService スコープ用）"
  value       = aws_ecs_service.backend.id
}

output "frontend_ecs_service_arn" {
  description = "フロント ECS サービスの ARN（CD の UpdateService スコープ用）"
  value       = aws_ecs_service.frontend.id
}

output "ecs_execution_role_arn" {
  description = "ECS タスク実行ロールの ARN（CD の iam:PassRole スコープ用）"
  value       = aws_iam_role.execution.arn
}

output "ecs_task_role_arn" {
  description = "ECS タスクロールの ARN（CD の iam:PassRole スコープ用）"
  value       = aws_iam_role.task.arn
}
