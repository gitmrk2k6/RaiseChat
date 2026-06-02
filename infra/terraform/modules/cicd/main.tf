# ===========================================================================
# CI/CD（⑤Step6）— GitHub Actions から AWS への OIDC 接続と最小権限デプロイロール。
#
# 静的な AWS アクセスキーをリポジトリ secret に置かず、GitHub Actions が発行する
# 短命の OIDC トークンを sts:AssumeRoleWithWebIdentity で交換して認証する
# （infrastructure.md §7 / §10 のシークレット最小化方針）。
#
# 信頼ポリシーは「このリポジトリの main ブランチで動いた job だけ」に絞り、
# 権限ポリシーは ecs module の output（ECR / ECS サービス / IAM ロール ARN）で
# この環境のリソースだけに絞る。author-only（apply は infra と同時）。
# ===========================================================================

# --- OIDC provider ----------------------------------------------------------
# GitHub Actions の OIDC 発行者。アカウント単位で一意なので、既存があれば
# create_oidc_provider=false で再利用する。thumbprint_list は AWS 側が自動取得
# するため指定しない（証明書ローテーションでの陳腐化を避ける）。
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  tags = {
    Name = "${var.name_prefix}-github-oidc"
  }
}

locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.existing_oidc_provider_arn
}

# --- デプロイロール（GitHub Actions が assume する）-------------------------
data "aws_iam_policy_document" "assume" {
  statement {
    sid     = "GitHubOidcAssume"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    # aud は固定。sub で「対象リポジトリの対象ブランチ」に限定する。
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_owner}/${var.github_repo}:ref:refs/heads/${var.github_branch}"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "${var.name_prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.assume.json

  tags = {
    Name = "${var.name_prefix}-github-deploy"
  }
}

# --- デプロイ権限（最小権限）------------------------------------------------
data "aws_iam_policy_document" "deploy" {
  # ECR ログイン用トークン取得。リソース指定不可のため "*"。
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # イメージの push / pull は対象リポジトリ ARN だけに絞る。
  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = var.ecr_repository_arns
  }

  # サービスのローリング更新（force-new-deployment）。対象サービス ARN に限定。
  statement {
    sid    = "EcsUpdateService"
    effect = "Allow"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
    ]
    resources = var.ecs_service_arns
  }

  # タスク定義の登録・参照はリソースレベル権限が無いため "*"。
  statement {
    sid    = "EcsTaskDefinition"
    effect = "Allow"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
    ]
    resources = ["*"]
  }

  # タスク定義に execution / task ロールを紐付けるための PassRole。
  # 対象ロール ARN に絞り、さらに渡し先サービスを ECS タスクに限定する。
  statement {
    sid       = "PassEcsRoles"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = var.ecs_pass_role_arns

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${var.name_prefix}-github-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
