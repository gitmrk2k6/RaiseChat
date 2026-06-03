output "bucket_name" {
  description = "アプリ用 S3 バケット名（ECS の S3_BUCKET 環境変数とタスクロールのスコープに使う）"
  value       = aws_s3_bucket.app.id
}

output "bucket_arn" {
  description = "アプリ用 S3 バケットの ARN"
  value       = aws_s3_bucket.app.arn
}

output "public_base_url" {
  description = "公開オブジェクトのベース URL（アプリの S3_PUBLIC_BASE_URL に渡す。<base>/<key> でアバター/添付を配信）"
  value       = "https://${aws_s3_bucket.app.id}.s3.${data.aws_region.current.name}.amazonaws.com"
}
