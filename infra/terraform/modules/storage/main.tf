# ===========================================================================
# storage module — アプリ用 S3 バケット（アバター F-02 / 添付 F-10）
#
# 正とする設計: docs/infrastructure.md §4（S3）。ECS タスクロールの S3 権限スコープ
# （modules/ecs）と、アプリの S3_BUCKET / S3_PUBLIC_BASE_URL 環境変数の供給元。
#
# なぜ公開読み取りか:
#   アプリは S3 オブジェクトを「公開 URL 直リンク」で配信する
#   （backend StorageProperties#resolvePublicBaseUrl → S3ObjectStorage が
#    publicBaseUrl + "/" + key を返す）。presign を使わないため、オブジェクトの
#   GetObject を公開してブラウザ <img> / ダウンロードから直接読めるようにする。
#
#   ※ これは author-only の短命 E2E 検証を前提とした割り切り。本番恒久運用に移す
#     場合は、バケットを非公開のままにして CloudFront + OAC を前に置き、
#     public_base_url を CloudFront ドメインに差し替える（公開ポリシーは外す）。
#
# author-only / on-demand 方針:
#   force_destroy = true。検証後の terraform destroy でオブジェクトごと削除し、
#   「全クリーンアップ（残存ゼロ）」を保証する。
# ===========================================================================

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

# バケット名は全世界で一意でなければならない。name_prefix にアカウント ID を付けて
# 衝突を避けつつ決定的にする（例: raisechat-dev-storage-411786661058）。
resource "aws_s3_bucket" "app" {
  bucket        = "${var.name_prefix}-storage-${data.aws_caller_identity.current.account_id}"
  force_destroy = true

  tags = {
    Name = "${var.name_prefix}-storage"
  }
}

# 公開ポリシーを許可するため、バケットレベルの Block Public Access を全て無効化する。
# （アカウントレベル BPA は未設定であることを前提。設定されている場合はそちらの解除も必要）
resource "aws_s3_bucket_public_access_block" "app" {
  bucket = aws_s3_bucket.app.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

# オブジェクトの GetObject のみ公開（読み取り専用）。書き込み・一覧は許可しない。
# アバター/添付の <img> 表示・ダウンロードに必要な最小公開。
data "aws_iam_policy_document" "public_read" {
  statement {
    sid       = "PublicReadGetObject"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.app.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "public_read" {
  bucket = aws_s3_bucket.app.id
  policy = data.aws_iam_policy_document.public_read.json

  # BPA を先に無効化してからでないとポリシー適用が拒否される。
  depends_on = [aws_s3_bucket_public_access_block.app]
}
