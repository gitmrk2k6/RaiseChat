#!/bin/bash
# LocalStack の起動完了後に実行される初期化スクリプト。
# アバター画像用バケットを作成し、URL で直接参照できるよう公開 read ポリシーを設定する。
set -euo pipefail

BUCKET="${S3_AVATAR_BUCKET:-raisechat-avatars}"

awslocal s3 mb "s3://${BUCKET}" || true

# avatarUrl（http://localhost:4566/<bucket>/<key>）を認証なしで取得できるようにする。
awslocal s3api put-bucket-policy --bucket "${BUCKET}" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [
    {
      \"Sid\": \"PublicReadGetObject\",
      \"Effect\": \"Allow\",
      \"Principal\": \"*\",
      \"Action\": \"s3:GetObject\",
      \"Resource\": \"arn:aws:s3:::${BUCKET}/*\"
    }
  ]
}"

echo "LocalStack init: bucket '${BUCKET}' is ready."
