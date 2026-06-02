output "state_bucket_name" {
  description = "envs/*/backend.tf の bucket に設定する値"
  value       = aws_s3_bucket.tfstate.id
}

output "lock_table_name" {
  description = "envs/*/backend.tf の dynamodb_table に設定する値"
  value       = aws_dynamodb_table.tflock.name
}

output "aws_region" {
  description = "envs/*/backend.tf の region に設定する値"
  value       = var.aws_region
}
