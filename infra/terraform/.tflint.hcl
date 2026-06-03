# tflint 共通設定（infra/terraform 配下の全スタックで共有）
#
# CI（.github/workflows/infra-ci.yml）では各スタックを `--chdir=<dir>` で解析するため、
# この設定ファイルは絶対パスを `TFLINT_CONFIG_FILE` で渡して共有する。
# ローカルでも `tflint --init` 後に同様に解析できる（README 参照）。

config {
  # モジュール呼び出し先まで再帰的に解析しない（各スタックの呼び出し側を検査する方針）。
  call_module_type = "local"
}

# Terraform 言語のベストプラクティス一式（命名・未使用宣言・非推奨構文など）。
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# AWS リソース固有の検査（不正なインスタンスタイプ・必須属性漏れ等）。
# `tflint --init` で下記バージョンのプラグインを取得する。
plugin "aws" {
  enabled = true
  version = "0.47.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
