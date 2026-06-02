output "sns_topic_arn" {
  description = "アラーム通知先 SNS トピックの ARN。apply 後にメール / Slack 等の購読を手動追加する"
  value       = aws_sns_topic.alerts.arn
}

output "dashboard_name" {
  description = "CloudWatch ダッシュボード名（コンソールの CloudWatch > Dashboards で開く）"
  value       = aws_cloudwatch_dashboard.this.dashboard_name
}

output "alarm_names" {
  description = "作成した CloudWatch アラーム名の一覧"
  value = concat(
    [for a in aws_cloudwatch_metric_alarm.ecs_cpu_high : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.ecs_memory_high : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.ecs_running_task_low : a.alarm_name],
    [for a in aws_cloudwatch_metric_alarm.alb_unhealthy_hosts : a.alarm_name],
    [aws_cloudwatch_metric_alarm.alb_5xx.alarm_name],
  )
}
