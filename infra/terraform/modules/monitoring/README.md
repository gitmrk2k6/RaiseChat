# modules/monitoring

RaiseChat の監視・オブザーバビリティ（⑤Step7）。**CloudWatch アラーム / ダッシュボード / SNS
通知トピック**を IaC 化する。ログ集約（CloudWatch Logs）は [`modules/ecs`](../ecs/README.md) で
実装済みで、本モジュールは**アラームと可視化、通知の配線**を担う。
設計の正は [docs/infrastructure.md §9](../../../../docs/infrastructure.md)。

## 責務

- **SNS トピック** (`<prefix>-alerts`): アラームの通知先。**購読（メール / Slack 等）は apply 後に
  手動追加**する（宛先やシークレットを state・コードに残さないため）。各アラームの `alarm_actions` /
  `ok_actions` をこのトピックに向ける
- **CloudWatch アラーム（フルセット）**:
  | アラーム | メトリクス（namespace） | 条件 |
  | --- | --- | --- |
  | ECS CPU 高負荷（backend / frontend）| `CPUUtilization`（AWS/ECS）| `> cpu_high_threshold`（既定 80%）|
  | ECS メモリ高負荷（backend / frontend）| `MemoryUtilization`（AWS/ECS）| `> memory_high_threshold`（既定 80%）|
  | 稼働タスク数の低下（backend / frontend）| `RunningTaskCount`（ECS/ContainerInsights）| `< desired_count`、欠損は breaching |
  | ターゲット異常ホスト（backend / frontend TG）| `UnHealthyHostCount`（AWS/ApplicationELB）| `>= 1` |
  | ALB 5xx 増 | `HTTPCode_Target_5XX_Count`（AWS/ApplicationELB）| `> alb_5xx_threshold`（既定 10/期間）|
- **CloudWatch ダッシュボード** (`<prefix>-overview`): 上記メトリクス＋ ALB の
  `RequestCount` / `TargetResponseTime`（レイテンシ）を 1 枚にまとめる

> `RunningTaskCount` は Container Insights のメトリクス。cluster 側で `containerInsights=enabled`
> 済み（[`modules/ecs`](../ecs/README.md)）。

## ecs module / envs との配線

CloudWatch のディメンションは ALB / TG の **ARN suffix** と ECS の **クラスタ名 / サービス名**で
指定するため、`modules/ecs` の output と envs の変数を配線する。

```
ecs.ecs_cluster_name                    ─▶ cluster_name
ecs.ecs_service_name                    ─▶ backend_service_name
ecs.frontend_ecs_service_name           ─▶ frontend_service_name
ecs.alb_arn_suffix                      ─▶ alb_arn_suffix
ecs.backend_target_group_arn_suffix     ─▶ backend_target_group_arn_suffix
ecs.frontend_target_group_arn_suffix    ─▶ frontend_target_group_arn_suffix
var.ecs_desired_count / var.frontend_desired_count ─▶ *_desired_count（タスク低下の閾値）
```

## 入力（主なもの）

| 変数 | 既定 | 用途 |
| --- | --- | --- |
| `cpu_high_threshold` | `80` | ECS CPU アラーム閾値（%）|
| `memory_high_threshold` | `80` | ECS メモリアラーム閾値（%）|
| `alb_5xx_threshold` | `10` | 評価期間あたりの 5xx 合計閾値 |
| `alarm_period` | `300` | 各アラームの評価期間（秒）|
| `alarm_evaluation_periods` | `2` | 発火に必要な連続違反期間数 |

## 出力

| 出力 | 用途 |
| --- | --- |
| `sns_topic_arn` | apply 後に購読を追加するトピック ARN |
| `dashboard_name` | CloudWatch コンソールで開くダッシュボード名 |
| `alarm_names` | 作成したアラーム名の一覧 |

## apply 方針

author-only / オンデマンド。本モジュール追加時点では **apply せず** `terraform fmt / validate /
tflint` まで（無課金）。apply 後に SNS トピックへ購読（メール等）を手動で追加すると通知が届く。
