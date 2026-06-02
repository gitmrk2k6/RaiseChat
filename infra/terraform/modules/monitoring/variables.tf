variable "name_prefix" {
  description = "リソース命名の接頭辞（例: raisechat-dev）。呼び出し元の local.name_prefix を渡す"
  type        = string
}

variable "aws_region" {
  description = "ダッシュボードのウィジェットが参照するリージョン（envs の var.aws_region を渡す）"
  type        = string
}

# --- 監視対象（ecs module の output / envs の変数を配線）------------------------
variable "cluster_name" {
  description = "ECS クラスタ名（AWS/ECS・ECS/ContainerInsights メトリクスの ClusterName ディメンション）"
  type        = string
}

variable "backend_service_name" {
  description = "バックエンド ECS サービス名（ServiceName ディメンション）"
  type        = string
}

variable "frontend_service_name" {
  description = "フロント ECS サービス名（ServiceName ディメンション）"
  type        = string
}

variable "backend_desired_count" {
  description = "バックエンドの desired count。RunningTaskCount 低下アラームの閾値に使う"
  type        = number
}

variable "frontend_desired_count" {
  description = "フロントの desired count。RunningTaskCount 低下アラームの閾値に使う"
  type        = number
}

variable "alb_arn_suffix" {
  description = "ALB の ARN suffix（AWS/ApplicationELB の LoadBalancer ディメンション）"
  type        = string
}

variable "backend_target_group_arn_suffix" {
  description = "バックエンド TG の ARN suffix（TargetGroup ディメンション）"
  type        = string
}

variable "frontend_target_group_arn_suffix" {
  description = "フロント TG の ARN suffix（TargetGroup ディメンション）"
  type        = string
}

# --- アラーム閾値・評価設定（既定で運用に無難な値。tfvars で調整可）-----------
variable "cpu_high_threshold" {
  description = "ECS CPUUtilization アラームの閾値（%）"
  type        = number
  default     = 80
}

variable "memory_high_threshold" {
  description = "ECS MemoryUtilization アラームの閾値（%）"
  type        = number
  default     = 80
}

variable "alb_5xx_threshold" {
  description = "ALB 5xx（HTTPCode_Target_5XX_Count）の評価期間あたり合計の閾値"
  type        = number
  default     = 10
}

variable "alarm_period" {
  description = "各アラームの評価期間（秒）"
  type        = number
  default     = 300
}

variable "alarm_evaluation_periods" {
  description = "アラーム発火に必要な連続違反期間数"
  type        = number
  default     = 2
}
