# ===========================================================================
# 運用 bastion（⑤Step8）— Ansible プロビジョニングの題材（infrastructure.md §8.1 / §12.1）
#
# ECS Fargate は OS 層を管理しないため Ansible の出番が薄い。そこで RDS / Redis への
# 踏み台を兼ねた運用 bastion EC2 を立て、その OS / ミドルウェア構成を Ansible で管理する。
#
# 接続は SSM Session Manager（インバウンド SSH を開けない・§10 シークレット最小化）。
# bastion は public subnet に置きパブリック IP を持つが、SG のインバウンドは無し。SSM
# エージェントが IGW 経由の egress 443 で SSM に接続するため NAT も interface endpoint も
# 不要（最小コスト）。Ansible の実接続は aws_ssm connection plugin を使う（README 参照）。
# ===========================================================================

# 最新の Amazon Linux 2023 AMI（SSM エージェント同梱）を SSM パブリックパラメータから取得。
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# --- SG（インバウンド無し / egress は SSM・パッケージ取得・踏み台先のみ）---------
resource "aws_security_group" "bastion" {
  name        = "${var.name_prefix}-bastion-sg"
  description = "Bastion SG: no inbound, egress to SSM/packages and RDS/Redis"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-bastion-sg"
  }
}

# SSM 接続・dnf でのパッケージ取得はいずれも HTTPS。
resource "aws_vpc_security_group_egress_rule" "bastion_https" {
  security_group_id = aws_security_group.bastion.id
  description       = "HTTPS for SSM agent and package repos"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# 踏み台として RDS / Redis へ向ける egress（宛先 SG を参照）。
resource "aws_vpc_security_group_egress_rule" "bastion_to_rds" {
  security_group_id            = aws_security_group.bastion.id
  description                  = "PostgreSQL to RDS SG"
  referenced_security_group_id = var.rds_sg_id
  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "bastion_to_redis" {
  security_group_id            = aws_security_group.bastion.id
  description                  = "Redis to Redis SG"
  referenced_security_group_id = var.redis_sg_id
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  ip_protocol                  = "tcp"
}

# RDS / Redis 側に bastion SG からの ingress を追加（踏み台経路）。
resource "aws_vpc_security_group_ingress_rule" "rds_from_bastion" {
  security_group_id            = var.rds_sg_id
  description                  = "PostgreSQL ${var.db_port} from bastion SG"
  referenced_security_group_id = aws_security_group.bastion.id
  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_bastion" {
  security_group_id            = var.redis_sg_id
  description                  = "Redis ${var.redis_port} from bastion SG"
  referenced_security_group_id = aws_security_group.bastion.id
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  ip_protocol                  = "tcp"
}

# --- IAM（SSM Session Manager）----------------------------------------------
data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "bastion" {
  name               = "${var.name_prefix}-bastion-role"
  assume_role_policy = data.aws_iam_policy_document.assume.json

  tags = {
    Name = "${var.name_prefix}-bastion-role"
  }
}

# SSM Session Manager に必要な最小権限の AWS マネージドポリシー。
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.bastion.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "bastion" {
  name = "${var.name_prefix}-bastion-profile"
  role = aws_iam_role.bastion.name
}

# --- EC2 --------------------------------------------------------------------
resource "aws_instance" "bastion" {
  ami                         = data.aws_ssm_parameter.al2023_ami.value
  instance_type               = var.instance_type
  subnet_id                   = var.public_subnet_id
  vpc_security_group_ids      = [aws_security_group.bastion.id]
  iam_instance_profile        = aws_iam_instance_profile.bastion.name
  associate_public_ip_address = true

  # IMDSv2 を必須化（トークン無しのメタデータ参照を拒否）。
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    encrypted   = true
    volume_size = 8
    volume_type = "gp3"
  }

  tags = {
    Name = "${var.name_prefix}-bastion"
  }
}
