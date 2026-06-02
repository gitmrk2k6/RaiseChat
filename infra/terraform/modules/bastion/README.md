# modules/bastion

RaiseChat の運用 bastion（⑤Step8）。**Ansible プロビジョニングの題材**であり、private に置いた
RDS / Redis への**踏み台**を兼ねる。設計の正は
[docs/infrastructure.md §8.1 / §12.1](../../../../docs/infrastructure.md)。

ECS Fargate は OS 層を管理しないため Ansible の出番が薄い。そこで運用 bastion EC2 を立て、
その OS / ミドルウェア構成を [`infra/ansible`](../../../ansible/README.md) の role で管理する
（Terraform = bastion の「存在」、Ansible = bastion の「中身」）。

## 責務

- **EC2**: Amazon Linux 2023（最新 AMI を SSM パブリックパラメータから取得・SSM エージェント同梱）、
  既定 `t3.micro`、public subnet・パブリック IP。IMDSv2 必須・ルート EBS 暗号化
- **IAM**: `AmazonSSMManagedInstanceCore` を付けたインスタンスプロファイル →
  **SSM Session Manager** で接続（インバウンド SSH を開けない・§10 シークレット最小化）
- **SG**: **インバウンド無し**。egress は (1) HTTPS 443（SSM エージェント・dnf パッケージ取得）、
  (2) RDS 5432 / Redis 6379（踏み台先 SG を参照）
- **踏み台経路**: RDS / Redis の SG に bastion SG からの ingress を追加（network 所有 SG に rule を
  足す流儀は ecs module の `ecs_sg ← ALB` と同じ）

## なぜ public subnet + SSM か

SSM エージェントは**アウトバウンド 443** で SSM に接続する。public subnet + パブリック IP なら
IGW 経由の egress 443 で接続でき、**NAT Gateway も interface endpoint も不要**（どちらも課金が
かさむ）。インバウンドは一切開けないため、public 配置でも SSH 露出はない。

## network / data module との配線

```
network.vpc_id            ─▶ vpc_id（bastion SG）
network.public_subnet_ids[0] ─▶ public_subnet_id（bastion 配置先）
network.rds_sg_id         ─▶ rds_sg_id（bastion → RDS ingress 追加）
network.redis_sg_id       ─▶ redis_sg_id（bastion → Redis ingress 追加）
data.db_instance_port     ─▶ db_port
data.redis_port           ─▶ redis_port
```

## 接続と踏み台（apply 後）

```bash
# 1. bastion にシェル接続
aws ssm start-session --target <instance_id>      # = output.ssm_start_session_command

# 2. RDS への踏み台（ローカル 15432 → RDS:5432）
aws ssm start-session --target <instance_id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<rds_endpoint>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
```

## Ansible との接続

ロール自体は [`infra/ansible`](../../../ansible/README.md) で **molecule + Docker** により検証する
（クラウド非依存・無課金）。実 bastion へ適用する際は SSH を開けないため、Ansible の
`community.aws.aws_ssm` connection plugin で SSM 経由接続する想定（手順は ansible 側 README）。

## apply 方針

author-only / オンデマンド。本モジュール追加時点では **apply せず** `terraform fmt / validate /
tflint` まで（無課金）。
