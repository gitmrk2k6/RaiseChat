# --- RDS for PostgreSQL ----------------------------------------------------
output "db_instance_endpoint" {
  description = "RDS のエンドポイント（host:port 形式）"
  value       = aws_db_instance.postgres.endpoint
}

output "db_instance_address" {
  description = "RDS のホスト名（ポートを含まない）"
  value       = aws_db_instance.postgres.address
}

output "db_instance_port" {
  description = "RDS のポート（5432）"
  value       = aws_db_instance.postgres.port
}

output "db_name" {
  description = "初期作成したデータベース名"
  value       = aws_db_instance.postgres.db_name
}

output "db_username" {
  description = "マスターユーザー名"
  value       = aws_db_instance.postgres.username
}

output "db_master_user_secret_arn" {
  description = "マスターパスワードを保管する Secrets Manager シークレットの ARN（§10。ECS タスク定義の secrets で参照する）"
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

# --- ElastiCache for Redis -------------------------------------------------
output "redis_primary_endpoint_address" {
  description = "Redis プライマリ（書き込み）エンドポイントのホスト名"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_reader_endpoint_address" {
  description = "Redis リーダーエンドポイントのホスト名（レプリカ構成時に読み取り分散用）"
  value       = aws_elasticache_replication_group.redis.reader_endpoint_address
}

output "redis_port" {
  description = "Redis のポート（6379）"
  value       = aws_elasticache_replication_group.redis.port
}
