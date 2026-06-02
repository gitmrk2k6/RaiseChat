# ===========================================================================
# 監視・オブザーバビリティ（⑤Step7）— CloudWatch アラーム / ダッシュボード / SNS
#
# infrastructure.md §9 に対応。ログ集約（CloudWatch Logs）は ecs module で実装済み。
# 本モジュールはアラームと可視化、通知の配線を担う。
#
# 通知は SNS トピックのみ作成し、購読（メール / Slack 等）は apply 後に手動で追加する
# 方針（シークレットや宛先を state・コードに残さないため）。アラームの alarm_actions /
# ok_actions をこのトピックに向ける。
# ===========================================================================

locals {
  # ECS サービス単位アラーム（CPU / メモリ / RunningTaskCount）を for_each で展開する。
  services = {
    backend = {
      service_name  = var.backend_service_name
      desired_count = var.backend_desired_count
    }
    frontend = {
      service_name  = var.frontend_service_name
      desired_count = var.frontend_desired_count
    }
  }

  # ALB ターゲットグループ単位アラーム（UnHealthyHostCount）用。
  target_groups = {
    backend  = var.backend_target_group_arn_suffix
    frontend = var.frontend_target_group_arn_suffix
  }
}

# --- 通知先トピック ---------------------------------------------------------
resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"

  tags = {
    Name = "${var.name_prefix}-alerts"
  }
}

# ===========================================================================
# アラーム
# ===========================================================================

# ECS CPU 高負荷（backend / frontend）。AWS/ECS は ClusterName + ServiceName で集計。
resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  for_each = local.services

  alarm_name          = "${var.name_prefix}-${each.key}-ecs-cpu-high"
  alarm_description   = "${each.key} の ECS タスク CPU 使用率が ${var.cpu_high_threshold}% を超過"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.cpu_high_threshold
  period              = var.alarm_period
  evaluation_periods  = var.alarm_evaluation_periods
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${var.name_prefix}-${each.key}-ecs-cpu-high"
  }
}

# ECS メモリ高負荷（backend / frontend）。
resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  for_each = local.services

  alarm_name          = "${var.name_prefix}-${each.key}-ecs-memory-high"
  alarm_description   = "${each.key} の ECS タスク メモリ使用率が ${var.memory_high_threshold}% を超過"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.memory_high_threshold
  period              = var.alarm_period
  evaluation_periods  = var.alarm_evaluation_periods
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${var.name_prefix}-${each.key}-ecs-memory-high"
  }
}

# 稼働タスク数の低下（backend / frontend）。desired を下回ったら異常とみなす。
# RunningTaskCount は Container Insights（ECS/ContainerInsights）のメトリクスで、
# cluster 側で containerInsights=enabled 済み。データ欠損は「タスク全滅」の疑いが
# 強いため breaching 扱いにする。
resource "aws_cloudwatch_metric_alarm" "ecs_running_task_low" {
  for_each = local.services

  alarm_name          = "${var.name_prefix}-${each.key}-ecs-running-task-low"
  alarm_description   = "${each.key} の稼働タスク数が desired (${each.value.desired_count}) を下回った"
  namespace           = "ECS/ContainerInsights"
  metric_name         = "RunningTaskCount"
  statistic           = "Minimum"
  comparison_operator = "LessThanThreshold"
  threshold           = each.value.desired_count
  period              = var.alarm_period
  evaluation_periods  = var.alarm_evaluation_periods
  treat_missing_data  = "breaching"

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${var.name_prefix}-${each.key}-ecs-running-task-low"
  }
}

# ターゲットの異常ホスト数（backend / frontend TG）。1 台でも unhealthy なら通知。
resource "aws_cloudwatch_metric_alarm" "alb_unhealthy_hosts" {
  for_each = local.target_groups

  alarm_name          = "${var.name_prefix}-${each.key}-alb-unhealthy-hosts"
  alarm_description   = "${each.key} ターゲットグループに異常ホストが存在"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  period              = var.alarm_period
  evaluation_periods  = var.alarm_evaluation_periods
  treat_missing_data  = "notBreaching"

  dimensions = {
    TargetGroup  = each.value
    LoadBalancer = var.alb_arn_suffix
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${var.name_prefix}-${each.key}-alb-unhealthy-hosts"
  }
}

# ALB 5xx（バックエンド由来の 5xx 応答）。評価期間あたりの合計が閾値超過で通知。
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.name_prefix}-alb-5xx-high"
  alarm_description   = "ALB ターゲット由来の 5xx が ${var.alb_5xx_threshold} 件/期間 を超過"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.alb_5xx_threshold
  period              = var.alarm_period
  evaluation_periods  = var.alarm_evaluation_periods
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${var.name_prefix}-alb-5xx-high"
  }
}

# ===========================================================================
# ダッシュボード — アラーム対象 ＋ ALB レイテンシ / リクエスト数を 1 枚に
# ===========================================================================
resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.name_prefix}-overview"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "ECS CPUUtilization (%)"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", var.backend_service_name],
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", var.frontend_service_name],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "ECS MemoryUtilization (%)"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/ECS", "MemoryUtilization", "ClusterName", var.cluster_name, "ServiceName", var.backend_service_name],
            ["AWS/ECS", "MemoryUtilization", "ClusterName", var.cluster_name, "ServiceName", var.frontend_service_name],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "ECS RunningTaskCount"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Minimum"
          period = 300
          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", var.cluster_name, "ServiceName", var.backend_service_name],
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", var.cluster_name, "ServiceName", var.frontend_service_name],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "ALB Host Count (Healthy / UnHealthy)"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Maximum"
          period = 300
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", var.backend_target_group_arn_suffix, "LoadBalancer", var.alb_arn_suffix],
            ["AWS/ApplicationELB", "UnHealthyHostCount", "TargetGroup", var.backend_target_group_arn_suffix, "LoadBalancer", var.alb_arn_suffix],
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", var.frontend_target_group_arn_suffix, "LoadBalancer", var.alb_arn_suffix],
            ["AWS/ApplicationELB", "UnHealthyHostCount", "TargetGroup", var.frontend_target_group_arn_suffix, "LoadBalancer", var.alb_arn_suffix],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "ALB Requests & 5xx (count/period)"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Sum"
          period = 300
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix],
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.alb_arn_suffix],
            ["AWS/ApplicationELB", "HTTPCode_ELB_5XX_Count", "LoadBalancer", var.alb_arn_suffix],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "ALB TargetResponseTime (s)"
          region = var.aws_region
          view   = "timeSeries"
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix],
          ]
        }
      },
    ]
  })
}
