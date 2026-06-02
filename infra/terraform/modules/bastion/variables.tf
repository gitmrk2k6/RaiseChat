variable "name_prefix" {
  description = "リソース命名の接頭辞（例: raisechat-dev）。呼び出し元の local.name_prefix を渡す"
  type        = string
}

variable "vpc_id" {
  description = "bastion SG を作成する VPC ID（network module の出力）"
  type        = string
}

variable "public_subnet_id" {
  description = "bastion を配置する public subnet ID。SSM は IGW 経由の egress 443 で接続するため NAT 不要"
  type        = string
}

# RDS / Redis は private で ECS SG からのみ ingress 許可。bastion を踏み台にするため、
# 両 SG に bastion SG からの ingress を足す（network 所有 SG に rule を追加する流儀は
# ecs module の ecs_sg ← ALB 追加と同じ）。
variable "rds_sg_id" {
  description = "RDS の SG ID。bastion SG からの 5432 ingress を追加する"
  type        = string
}

variable "redis_sg_id" {
  description = "Redis の SG ID。bastion SG からの 6379 ingress を追加する"
  type        = string
}

variable "db_port" {
  description = "RDS ポート（踏み台 egress / RDS ingress に使う）"
  type        = number
  default     = 5432
}

variable "redis_port" {
  description = "Redis ポート（踏み台 egress / Redis ingress に使う）"
  type        = number
  default     = 6379
}

variable "instance_type" {
  description = "bastion のインスタンスタイプ。踏み台用途のため最小で十分"
  type        = string
  default     = "t3.micro"
}
