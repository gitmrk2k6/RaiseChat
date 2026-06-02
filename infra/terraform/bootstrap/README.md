# bootstrap — Terraform state backend

Terraform の**リモート state 置き場**（S3 バケット + DynamoDB ロックテーブル）を作る専用スタック。

- このスタックだけは **local state**（`bootstrap/terraform.tfstate`）で管理する。
  state 置き場を作るスタックがそのリモート state を必要とする鶏卵問題を避けるため。
- 作成物は **S3（versioning + 暗号化 + 公開遮断）** と **DynamoDB（`LockID` ハッシュキー / 従量課金）**。
  保存量・リクエストともごく僅かなのでほぼ無料。
- `envs/*/backend.tf` が、ここで作ったバケット名 / テーブル名を参照する。

## 使い方

```bash
terraform init
terraform apply          # 初回のみ。S3 + DynamoDB を作成
terraform output         # bucket / table 名を確認 → envs/*/backend.tf と一致させる
```

検証だけしたい場合は実 AWS 不要で:

```bash
terraform init -backend=false && terraform validate
```

> S3 バケットには `prevent_destroy` を設定済み。state を失うと全環境の管理情報を失うため、
> 通常 `terraform destroy` で消さない。
