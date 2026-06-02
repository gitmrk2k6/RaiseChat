# modules/network

RaiseChat の AWS ネットワーク基盤（VPC / 複数AZ サブネット / ルーティング / セキュリティグループ）。
設計の正は [docs/infrastructure.md §5](../../../../docs/infrastructure.md)。

## 責務

- VPC（DNS 解決有効）
- 複数 AZ（既定 2）に **public / private** subnet を配置
- public は IGW 経由でインターネットへ。ALB を置く
- private は ECS / RDS / ElastiCache を置く。外部 egress は **単一 NAT Gateway（toggle）** 経由
- 無料の **S3 Gateway VPC エンドポイント**を private ルートテーブルに関連付け
- 最小権限のセキュリティグループ経路を構成

## SG 経路（最小権限）

```
internet ──443──▶ alb_sg
alb_sg   ──8080─▶ ecs_sg
ecs_sg   ──5432─▶ rds_sg
ecs_sg   ──6379─▶ redis_sg
```

- 受け口は「直前の層の SG」からのみ（`referenced_security_group_id`）。CIDR 開放は ALB の 443 だけ。
- RDS / Redis はインターネット非公開。ECS SG からのみ受ける。

## 入力

| 変数 | 型 | 既定 | 説明 |
| --- | --- | --- | --- |
| `name_prefix` | string | （必須）| リソース名接頭辞（例 `raisechat-dev`）|
| `vpc_cidr` | string | `10.0.0.0/16` | VPC CIDR |
| `azs_count` | number | `2` | 使う AZ 数（2 以上）|
| `enable_nat_gateway` | bool | `false` | true で単一 NAT GW + private default route を作成 |

## 出力

`vpc_id` / `vpc_cidr` / `public_subnet_ids` / `private_subnet_ids` /
`alb_sg_id` / `ecs_sg_id` / `rds_sg_id` / `redis_sg_id`

## 課金の注意

- **`enable_nat_gateway` は既定 `false`**。NAT Gateway / EIP は時間課金されるため、`terraform validate`
  や常時待機では作らない（author-only 方針）。
- private サブネットの ECS が ECR からイメージを pull する等、外部 egress が必要な
  **E2E デプロイ検証のときだけ `true`** にして apply し、検証後に destroy する。
- S3 Gateway エンドポイントは無料なので常設してよい。

## 呼び出し例（envs/dev）

```hcl
module "network" {
  source             = "../../modules/network"
  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  azs_count          = var.azs_count
  enable_nat_gateway = var.enable_nat_gateway
}
```
