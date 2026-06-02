# Step 2 以降で module を追加するたびに、必要な出力（VPC ID / ALB DNS 名など）を
# ここに追記する。

output "name_prefix" {
  description = "この環境のリソース命名接頭辞（以降の module 名付けの起点）"
  value       = local.name_prefix
}
