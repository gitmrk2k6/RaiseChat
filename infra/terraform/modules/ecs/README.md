# modules/ecs

RaiseChat のアプリ実行基盤（ECR / ALB / ECS Fargate / CloudWatch Logs / IAM / シークレット）。
設計の正は [docs/infrastructure.md §3・§4・§6.1・§9・§10](../../../../docs/infrastructure.md)。

## 責務

- **ECR**: バックエンドのイメージ置き場（`scan_on_push` / untagged 失効 / `force_delete`）
- **ALB**: 外部公開点。public subnet に置き、証明書があれば HTTPS:443（WSS 終端）、なければ
  HTTP:80 を target group へ転送。target group は `target_type=ip`・8080・**スティッキー（lb_cookie）**
- **ECS Fargate**: cluster / service（private subnet・`desired_count` 既定 2 で Multi-AZ）/ task definition
- **CloudWatch Logs**: `awslogs` ドライバで標準出力を集約（§9）
- **IAM**: 実行ロール（ECR pull / Logs / シークレット取得）＋ タスクロール（S3。静的キーを持たせない・§10）
- **シークレット**: `JWT_SECRET` を Secrets Manager に生成、Redis 接続情報を SSM Parameter Store に投入（§10）

## network / data module との配線

```
network.vpc_id              ─▶ target group
network.public_subnet_ids   ─▶ ALB
network.private_subnet_ids  ─▶ ECS service
network.alb_sg_id           ─▶ ALB
network.ecs_sg_id           ─▶ ECS service
data.db_instance_address    ─▶ SPRING_DATASOURCE_URL（host）
data.db_master_user_secret_arn ─▶ task def secrets（DB_USERNAME / DB_PASSWORD）
data.redis_primary_endpoint_address ─▶ SSM（REDIS_HOST）
```

## タスク定義で注入する環境変数（`application.yml` の変数名と一致）

| 種別 | 変数 | 供給元 |
| --- | --- | --- |
| environment | `SPRING_DATASOURCE_URL` | DB host/port/name から組み立て（yml の localhost を relaxed binding で上書き）|
| environment | `S3_BUCKET` / `AWS_REGION` | 変数 / provider region |
| environment | `S3_ENDPOINT=""` | 実 S3 利用のため LocalStack 既定を空で打ち消す |
| environment | `WS_ALLOWED_ORIGINS` / `APP_CORS_ALLOWED_ORIGINS` / `INVITE_BASE_URL` | 変数（空なら注入せずアプリ既定）|
| secrets | `DB_USERNAME` / `DB_PASSWORD` | RDS マネージドシークレットの JSON キー（`:username::` / `:password::`）|
| secrets | `JWT_SECRET` | Secrets Manager（本モジュールで生成）|
| secrets | `REDIS_HOST` / `REDIS_PORT` | SSM Parameter Store |

## アプリ側の前提（本モジュールが効くために必要）

- **S3 はタスクロール認証**: `StorageConfig` は `S3_ENDPOINT` が空のとき `endpointOverride` を外し
  `DefaultCredentialsProvider`（=タスクロール）に切り替える（§10）。
- **ヘルスチェック**: ALB は `/actuator/health` を叩く。Spring Boot Actuator を有効化し、
  Security で同パスを認証不要にしておく（§9）。

## ALB TLS（cert トグル）

- `certificate_arn` を指定 → HTTPS:443（WSS 終端）。HTTP:80 は 443 へ 301 リダイレクト。
- 未指定 → HTTP:80 を直接 target group へ転送。ドメイン/ACM 未整備でも ALB DNS 名で E2E できる
  author-only 用フォールバック。本番では証明書を渡して 443 終端にする（§3）。

## 課金の注意（author-only / on-demand）

- **ALB・Fargate タスク・NAT は存在するだけで時間課金される**。`terraform validate` / `plan` は
  無課金だが `apply` で実リソースが作られる。**apply は E2E デプロイ検証時のみ**・検証後 `destroy`。
- private の ECS が ECR/Logs へ出るには egress が要る。E2E 時は network の `enable_nat_gateway=true`
  と併用する（常時 NAT は課金のため通常 OFF）。
- `desired_count` を増やすほどタスク課金が増える。常時は最小、Multi-AZ 検証時のみ 2 以上に。

## 入力

| 変数 | 型 | 既定 | 説明 |
| --- | --- | --- | --- |
| `name_prefix` | string | （必須）| リソース名接頭辞（例 `raisechat-dev`）|
| `vpc_id` | string | （必須）| target group を置く VPC |
| `public_subnet_ids` | list(string) | （必須）| ALB を置く public subnet（2 つ以上）|
| `private_subnet_ids` | list(string) | （必須）| ECS を置く private subnet（2 つ以上）|
| `alb_sg_id` / `ecs_sg_id` | string | （必須）| ALB / ECS の SG |
| `db_endpoint_host` | string | （必須）| RDS ホスト名（SPRING_DATASOURCE_URL 用）|
| `db_port` / `db_name` | number / string | `5432` / `raisechat` | DB 接続情報 |
| `db_master_user_secret_arn` | string | （必須）| RDS シークレット ARN（DB 認証注入）|
| `redis_host` / `redis_port` | string / number | （必須）/ `6379` | Redis 接続情報（SSM 経由）|
| `s3_bucket` | string | `raisechat-avatars` | アバター/添付バケット名（タスクロールのスコープ）|
| `ws_allowed_origins` / `invite_base_url` | string | `""` | 空なら注入せずアプリ既定に委ねる |
| `container_image` | string | `""` | 空なら本モジュールの ECR の `:latest` |
| `container_port` | number | `8080` | アプリのポート |
| `cpu_architecture` | string | `X86_64` | Fargate アーキ（push イメージと一致させる）|
| `task_cpu` / `task_memory` | number | `512` / `1024` | タスクのサイジング |
| `desired_count` | number | `2` | 希望タスク数（§6.1 で 2 以上が基本）|
| `certificate_arn` | string | `""` | ACM 証明書 ARN（指定で HTTPS:443 終端）|
| `log_retention_days` | number | `14` | CloudWatch Logs 保持日数 |

## 出力

`ecr_repository_url` / `ecr_repository_name` / `alb_dns_name` / `alb_zone_id` / `alb_arn` /
`ecs_cluster_name` / `ecs_service_name` / `task_definition_arn` / `log_group_name` / `jwt_secret_arn`

## 呼び出し例（envs/dev）

```hcl
module "ecs" {
  source      = "../../modules/ecs"
  name_prefix = local.name_prefix

  vpc_id             = module.network.vpc_id
  public_subnet_ids  = module.network.public_subnet_ids
  private_subnet_ids = module.network.private_subnet_ids
  alb_sg_id          = module.network.alb_sg_id
  ecs_sg_id          = module.network.ecs_sg_id

  db_endpoint_host          = module.data.db_instance_address
  db_port                   = module.data.db_instance_port
  db_name                   = module.data.db_name
  db_master_user_secret_arn = module.data.db_master_user_secret_arn
  redis_host                = module.data.redis_primary_endpoint_address
  redis_port                = module.data.redis_port

  certificate_arn    = var.certificate_arn
  container_image    = var.container_image
  desired_count      = var.ecs_desired_count
  s3_bucket          = var.s3_bucket
  ws_allowed_origins = var.ws_allowed_origins
  invite_base_url    = var.invite_base_url
}
```
