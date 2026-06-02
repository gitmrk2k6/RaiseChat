# ---------------------------------------------------------------------------
# Terraform state 置き場（リモート backend）の実体を作る。
#   - S3 バケット: tfstate ファイルを保存。バージョニング＋暗号化＋公開遮断。
#   - DynamoDB テーブル: terraform apply 同時実行を防ぐ state ロック。
# どちらも従量課金がごく僅か（保存数 KB / ロックは数リクエスト）でほぼ無料。
# ---------------------------------------------------------------------------

resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name

  # 誤削除防止。state バケットは消えると全環境の管理情報を失うため必ず保護する。
  lifecycle {
    prevent_destroy = true
  }
}

# state ファイルの履歴を残し、誤上書き・破損から復旧できるようにする。
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

# 保存時暗号化（SSE-S3）。
resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# パブリックアクセスを全面遮断（state には接続情報が含まれ得るため必須）。
resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# state ロック用テーブル。キー名は Terraform S3 backend の規約どおり "LockID"。
# PAY_PER_REQUEST にして使った分だけ課金（ロックは極小なのでほぼ無料）。
resource "aws_dynamodb_table" "tflock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
