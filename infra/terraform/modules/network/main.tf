# ===========================================================================
# network module — VPC / 複数AZ subnet / ルーティング / セキュリティグループ
#
# 正とする設計: docs/infrastructure.md §5。
# 外部公開は ALB(public) のみ。ECS/RDS/Redis は private に置き、SG で
# 「直前の層からのみ受ける」最小権限経路（ALB→ECS→RDS/Redis）を組む。
# 共通タグは呼び出し元 provider の default_tags が自動付与するため、ここでは
# Name タグなど個別タグのみ付ける。
# ===========================================================================

# 利用可能な AZ から先頭 azs_count 個を採る。リージョン差異に依存しない。
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.azs_count)
}

# --- VPC -------------------------------------------------------------------
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.name_prefix}-vpc"
  }
}

# --- サブネット ------------------------------------------------------------
# public: ALB を置く。10.0.0.0/24, 10.0.1.0/24, …
resource "aws_subnet" "public" {
  count                   = var.azs_count
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.name_prefix}-public-${local.azs[count.index]}"
    Tier = "public"
  }
}

# private: ECS/RDS/Redis を置く。public とレンジを分ける（+10）。10.0.10.0/24, …
resource "aws_subnet" "private" {
  count             = var.azs_count
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = local.azs[count.index]

  tags = {
    Name = "${var.name_prefix}-private-${local.azs[count.index]}"
    Tier = "private"
  }
}

# --- インターネットゲートウェイ / public ルーティング ----------------------
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-public-rt"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count          = var.azs_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- NAT Gateway（toggle）/ private ルーティング ---------------------------
# author-only: 既定 OFF（count=0）。E2E 検証時のみ enable_nat_gateway=true で
# 単一 NAT を public subnet[0] に置き、private の egress を通す。
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? 1 : 0
  domain = "vpc"

  tags = {
    Name = "${var.name_prefix}-nat-eip"
  }
}

resource "aws_nat_gateway" "this" {
  count         = var.enable_nat_gateway ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "${var.name_prefix}-nat"
  }

  depends_on = [aws_internet_gateway.this]
}

# private は単一ルートテーブルを共有（単一 NAT 方針）。
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-private-rt"
  }
}

resource "aws_route" "private_nat" {
  count                  = var.enable_nat_gateway ? 1 : 0
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[0].id
}

resource "aws_route_table_association" "private" {
  count          = var.azs_count
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# --- S3 Gateway VPC エンドポイント -----------------------------------------
# 無料・常設。private から S3 への通信を NAT を経由させず直結し、転送量を削減する。
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]

  tags = {
    Name = "${var.name_prefix}-s3-endpoint"
  }
}

data "aws_region" "current" {}

# ===========================================================================
# セキュリティグループ（§5.2 最小権限経路）
#   internet ─443→ ALB ─8080→ ECS ─5432→ RDS / ─6379→ Redis
# 循環参照を避けるため、SG 本体と in/out ルールを分離（provider 5系の推奨リソース）。
# ===========================================================================

# --- ALB SG ---------------------------------------------------------------
resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb-sg"
  description = "ALB: allow HTTPS from internet"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-alb-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS/WSS from internet"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# HTTP:80 も常に開ける。理由は2つ:
#   - certificate_arn 未指定（ドメイン/ACM 未整備）のとき ecs module は HTTP:80 で
#     直接配信する（HTTP フォールバック）。
#   - certificate_arn 指定時は 80 を HTTPS:443 へリダイレクトするリスナーが立つ。
# どちらの場合も 80 を閉じていると到達できないため、SG は 80/443 を両方許可する。
resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from internet (fallback or redirect to HTTPS)"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  description       = "allow all egress"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- ECS SG ---------------------------------------------------------------
resource "aws_security_group" "ecs" {
  name        = "${var.name_prefix}-ecs-sg"
  description = "ECS tasks: allow app port from ALB only"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-ecs-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  security_group_id            = aws_security_group.ecs.id
  description                  = "app port 8080 from ALB SG"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_all" {
  security_group_id = aws_security_group.ecs.id
  description       = "allow all egress (ECR/S3/Logs/RDS/Redis)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- RDS SG ---------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = "RDS PostgreSQL: allow 5432 from ECS only"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-rds-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL 5432 from ECS SG"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

# --- Redis SG -------------------------------------------------------------
resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis-sg"
  description = "ElastiCache Redis: allow 6379 from ECS only"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${var.name_prefix}-redis-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_ecs" {
  security_group_id            = aws_security_group.redis.id
  description                  = "Redis 6379 from ECS SG"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
