# リモート state backend（bootstrap スタックが作成する S3 / DynamoDB を参照）。
#
# 【author-only での検証】backend を作らずに validate だけしたいときは:
#     terraform init -backend=false
#     terraform validate
#   とすると、この backend 設定は無視され実 AWS なしで構文・型検査ができる。
#
# 【実際に state を使い始めるとき】まず bootstrap を 1 回 apply し、
#   下の bucket / dynamodb_table 名が出力されたら、ここで:
#     terraform init   （backend へ初期化／移行）
#   を実行する。詳細は infra/terraform/README.md を参照。
terraform {
  backend "s3" {
    bucket         = "raisechat-tfstate"
    key            = "envs/dev/terraform.tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "raisechat-tflock"
    encrypt        = true
  }
}
