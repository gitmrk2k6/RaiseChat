output "deploy_role_arn" {
  description = "GitHub Actions が OIDC で assume するデプロイロールの ARN（workflow の role-to-assume に設定）"
  value       = aws_iam_role.deploy.arn
}

output "oidc_provider_arn" {
  description = "GitHub Actions 用 OIDC provider の ARN（本 module で作成 or 再利用したもの）"
  value       = local.oidc_provider_arn
}
