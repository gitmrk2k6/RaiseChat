output "bastion_instance_id" {
  description = "bastion の EC2 インスタンス ID（SSM の --target に指定）"
  value       = aws_instance.bastion.id
}

output "bastion_sg_id" {
  description = "bastion の SG ID"
  value       = aws_security_group.bastion.id
}

output "ssm_start_session_command" {
  description = "SSM Session Manager で bastion に接続するコマンド（apply 後の運用ヒント）"
  value       = "aws ssm start-session --target ${aws_instance.bastion.id}"
}
