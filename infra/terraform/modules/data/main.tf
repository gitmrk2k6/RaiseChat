# ===========================================================================
# data module — RDS for PostgreSQL / ElastiCache for Redis
#
# 正とする設計: docs/infrastructure.md §4 / §5 / §6.2 / §10。
# network module の出力（private_subnet_ids / rds_sg_id / redis_sg_id）を受けて
# private subnet にデータ層を配置する。外部公開はせず、ECS SG からのみ受ける。
# 共通タグは呼び出し元 provider の default_tags が自動付与するため、ここでは
# Name タグなど個別タグのみ付ける。
#
# author-only / on-demand 方針:
#   RDS・ElastiCache は存在するだけで時間課金される。apply は E2E デプロイ検証時
#   のみ行い、検証後に destroy する前提。冗長度（Multi-AZ / レプリカ）は変数で
#   既定 OFF（最小）にし、課金を最小化する。
# ===========================================================================

# --- サブネットグループ ----------------------------------------------------
# RDS / ElastiCache をどの subnet（= どの AZ）に置けるかを定義する。Multi-AZ 化に
# 備え、network の private subnet（複数 AZ）をそのまま渡す。
resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "${var.name_prefix}-db-subnet-group"
  }
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name_prefix}-redis-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "${var.name_prefix}-redis-subnet-group"
  }
}

# --- RDS for PostgreSQL ----------------------------------------------------
# パスワードは manage_master_user_password で AWS に委譲し、Secrets Manager に
# 自動生成・保管・ローテーションさせる（§10）。Terraform state に平文を残さない。
resource "aws_db_instance" "postgres" {
  identifier     = "${var.name_prefix}-postgres"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  # ストレージ: gp3。max を allocated より大きくすると自動スケールが効く。
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  # パスワードは渡さない。AWS が Secrets Manager 側で管理する。
  manage_master_user_password = true

  # 配置: private subnet + RDS SG（ECS SG からのみ 5432）。外部非公開。
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.rds_sg_id]
  publicly_accessible    = false
  port                   = 5432

  # 冗長化・バックアップ。既定は最小（author-only）。
  multi_az                = var.db_multi_az
  backup_retention_period = var.db_backup_retention_period
  deletion_protection     = var.db_deletion_protection
  skip_final_snapshot     = var.db_skip_final_snapshot

  auto_minor_version_upgrade = true
  apply_immediately          = true

  tags = {
    Name = "${var.name_prefix}-postgres"
  }
}

# --- ElastiCache for Redis -------------------------------------------------
# キャッシュ（cache-strategy.md）と Pub-Sub（realtime-design.md）兼用。
# replication_group を使い、num_cache_clusters を増やすだけでレプリカ＋自動
# フェイルオーバー＋Multi-AZ に昇格できる形にする（§6.2「まず単一、後でレプリカ」）。
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.name_prefix}-redis"
  description          = "RaiseChat Redis (cache + pub/sub)"

  engine         = "redis"
  engine_version = var.redis_engine_version
  node_type      = var.redis_node_type
  port           = 6379

  # cluster mode 無効（単一シャード）。ノード数 1 で単一、2 以上でレプリカ構成。
  num_cache_clusters = var.redis_num_cache_clusters
  # ノードが複数のときだけ自動フェイルオーバー / Multi-AZ を有効化する。
  automatic_failover_enabled = var.redis_num_cache_clusters > 1
  multi_az_enabled           = var.redis_num_cache_clusters > 1

  # 配置: private subnet + Redis SG（ECS SG からのみ 6379）。
  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [var.redis_sg_id]
  parameter_group_name = "default.redis7"

  # 保存時暗号化は無料。転送時暗号化(AUTH token)は MVP では使わず private 隔離で守る。
  at_rest_encryption_enabled = true

  apply_immediately = true

  tags = {
    Name = "${var.name_prefix}-redis"
  }
}
