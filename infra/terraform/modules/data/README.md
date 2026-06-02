# modules/data

RaiseChat のデータ層（RDS for PostgreSQL / ElastiCache for Redis）。
設計の正は [docs/infrastructure.md §4・§5・§6.2・§10](../../../../docs/infrastructure.md)。

## 責務

- `aws_db_subnet_group` / `aws_elasticache_subnet_group`（network の private subnet に配置）
- **RDS for PostgreSQL**（17 系・gp3・暗号化）。パスワードは `manage_master_user_password` で
  **Secrets Manager に自動生成・保管・ローテーション**させ、state に平文を残さない（§10）
- **ElastiCache for Redis**（7 系・replication group）。キャッシュと Pub-Sub 兼用
- 配置はすべて private。受け口は network module の `rds_sg_id` / `redis_sg_id`（ECS SG からのみ）

## network module との配線

```
network.private_subnet_ids ─▶ db_subnet_group / redis_subnet_group
network.rds_sg_id          ─▶ aws_db_instance.vpc_security_group_ids
network.redis_sg_id        ─▶ aws_elasticache_replication_group.security_group_ids
```

## 冗長度（コスト）の扱い

- `db_multi_az` / `redis_num_cache_clusters` は **既定で最小**（`false` / `1`）。
- `redis_num_cache_clusters` を 2 以上にすると **自動フェイルオーバー＋Multi-AZ を自動有効化**する。
- 設計の Multi-AZ 目標（§6.2）は、E2E 検証時にこれらの変数を上げて担保する。

## 入力

| 変数 | 型 | 既定 | 説明 |
| --- | --- | --- | --- |
| `name_prefix` | string | （必須）| リソース名接頭辞（例 `raisechat-dev`）|
| `private_subnet_ids` | list(string) | （必須）| 配置する private subnet（2 つ以上）|
| `rds_sg_id` | string | （必須）| RDS にアタッチする SG |
| `redis_sg_id` | string | （必須）| Redis にアタッチする SG |
| `db_engine_version` | string | `17.4` | PostgreSQL バージョン |
| `db_instance_class` | string | `db.t4g.micro` | RDS インスタンスクラス |
| `db_allocated_storage` | number | `20` | 初期ストレージ（GiB）|
| `db_max_allocated_storage` | number | `100` | 自動スケール上限（GiB）|
| `db_name` | string | `raisechat` | 初期 DB 名 |
| `db_username` | string | `raisechat` | マスターユーザー名 |
| `db_multi_az` | bool | `false` | Multi-AZ 化（true で standby 追加・課金増）|
| `db_backup_retention_period` | number | `1` | 自動バックアップ保持日数 |
| `db_deletion_protection` | bool | `false` | 削除保護 |
| `db_skip_final_snapshot` | bool | `true` | destroy 時に最終スナップショットを取らない |
| `redis_engine_version` | string | `7.1` | Redis バージョン |
| `redis_node_type` | string | `cache.t4g.micro` | ノードタイプ |
| `redis_num_cache_clusters` | number | `1` | ノード数（2 以上で冗長化）|

## 出力

`db_instance_endpoint` / `db_instance_address` / `db_instance_port` / `db_name` /
`db_username` / `db_master_user_secret_arn` /
`redis_primary_endpoint_address` / `redis_reader_endpoint_address` / `redis_port`

> `db_master_user_secret_arn` は ECS タスク定義（Step4）の `secrets` から参照する。
> Redis 接続情報の SSM Parameter Store 投入も Step4(ECS) 側で扱う（このモジュールは DB 認証のみ所有）。

## 課金の注意

- **RDS / ElastiCache は存在するだけで時間課金される**。`terraform validate` / `plan` では
  課金されないが、`apply` するとリソースが作られる。author-only 方針のため、**apply は
  E2E デプロイ検証のときだけ**行い、検証後に `destroy` する。
- 冗長度（`db_multi_az` / `redis_num_cache_clusters`）を上げると更に課金が増えるため、
  常時は既定の最小のままにする。

## 呼び出し例（envs/dev）

```hcl
module "data" {
  source             = "../../modules/data"
  name_prefix        = local.name_prefix
  private_subnet_ids = module.network.private_subnet_ids
  rds_sg_id          = module.network.rds_sg_id
  redis_sg_id        = module.network.redis_sg_id

  db_instance_class        = var.db_instance_class
  db_multi_az              = var.db_multi_az
  redis_node_type          = var.redis_node_type
  redis_num_cache_clusters = var.redis_num_cache_clusters
}
```
