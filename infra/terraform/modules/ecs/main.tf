# ===========================================================================
# ecs module — ECR / ALB / ECS Fargate / CloudWatch Logs / IAM / シークレット
#
# 正とする設計: docs/infrastructure.md §3 / §4 / §6.1 / §9 / §10 / §12.2。
# アプリ層（Spring Boot バックエンド + Next.js フロントエンド）をステートレスに
# Fargate で複数タスク・Multi-AZ 実行し、単一 ALB で WSS/443（または HTTP/80
# フォールバック）を終端する。ALB のパスルーティングで同一オリジン配信する（§12.2 / Step5）:
#   default        → frontend（Next.js standalone, port 3000）
#   /api/*         → backend（Spring Boot REST, 8080）
#   /ws・/ws/*     → backend（SockJS / STOMP, 8080）
# これによりブラウザは相対 URL で REST/WS を叩け、CORS を踏まない。
# network（public/private subnet・alb_sg・ecs_sg）と data（DB シークレット・Redis）の
# 出力を配線する。共通タグは呼び出し元 provider の default_tags が自動付与する。
#
# author-only / on-demand 方針:
#   ALB・Fargate・NAT は存在するだけで時間課金される。apply は E2E デプロイ検証時のみ・
#   検証後に destroy する前提。validate / plan は無課金。private の ECS が ECR/Logs へ
#   出るには NAT が要るため、E2E 時は network の enable_nat_gateway=true と併用する。
# ===========================================================================

data "aws_region" "current" {}

locals {
  container_name          = "${var.name_prefix}-backend"
  frontend_container_name = "${var.name_prefix}-frontend"

  # container_image 未指定なら本モジュールの ECR の :latest を使う（push 後に apply する想定）。
  image          = var.container_image != "" ? var.container_image : "${aws_ecr_repository.backend.repository_url}:latest"
  frontend_image = var.frontend_container_image != "" ? var.frontend_container_image : "${aws_ecr_repository.frontend.repository_url}:latest"

  # application.yml の spring.datasource.url（localhost 固定）を環境変数で上書きする。
  # Spring の relaxed binding により SPRING_DATASOURCE_URL が yml 値を上書きするためコード変更不要。
  jdbc_url = "jdbc:postgresql://${var.db_endpoint_host}:${var.db_port}/${var.db_name}"

  https_enabled = var.certificate_arn != ""

  # タスク定義の environment（非機密）。値が空のものはアプリ既定に委ねるため注入しない。
  base_env = [
    { name = "SPRING_DATASOURCE_URL", value = local.jdbc_url },
    { name = "S3_BUCKET", value = var.s3_bucket },
    { name = "AWS_REGION", value = data.aws_region.current.name },
    # 実 S3 を使うため LocalStack 向けの endpoint 既定（localhost:4566）を空で打ち消す。
    # 空のとき StorageConfig は endpointOverride を外しタスクロール認証に切り替える。
    { name = "S3_ENDPOINT", value = "" },
  ]
  optional_env = concat(
    var.ws_allowed_origins != "" ? [
      { name = "WS_ALLOWED_ORIGINS", value = var.ws_allowed_origins },
      { name = "APP_CORS_ALLOWED_ORIGINS", value = var.ws_allowed_origins },
    ] : [],
    var.invite_base_url != "" ? [
      { name = "INVITE_BASE_URL", value = var.invite_base_url },
    ] : [],
    # 公開ベース URL。空のとき StorageProperties は endpoint/bucket から導出するが、
    # 実 S3（S3_ENDPOINT 空）では導出が壊れる（/bucket になる）ため必ず渡す。
    var.s3_public_base_url != "" ? [
      { name = "S3_PUBLIC_BASE_URL", value = var.s3_public_base_url },
    ] : [],
  )
  container_env = concat(local.base_env, local.optional_env)
}

# ===========================================================================
# ECR — バックエンドのコンテナイメージ置き場（§4）
# ===========================================================================
resource "aws_ecr_repository" "backend" {
  name                 = "${var.name_prefix}-backend"
  image_tag_mutability = "MUTABLE"
  # author-only: destroy 時にイメージごと消せるようにする（空でなくても削除可）。
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.name_prefix}-backend"
  }
}

# untagged（古いレイヤ）が溜まらないよう失効させ、ストレージ課金を抑える。
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "expire untagged images older than 7 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 7
      }
      action = { type = "expire" }
    }]
  })
}

# フロントエンド（Next.js standalone）のイメージ置き場。スキャン・失効方針は backend と同じ。
resource "aws_ecr_repository" "frontend" {
  name                 = "${var.name_prefix}-frontend"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.name_prefix}-frontend"
  }
}

resource "aws_ecr_lifecycle_policy" "frontend" {
  repository = aws_ecr_repository.frontend.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "expire untagged images older than 7 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 7
      }
      action = { type = "expire" }
    }]
  })
}

# ===========================================================================
# CloudWatch Logs — アプリの標準出力集約先（§9）
# ===========================================================================
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.name_prefix}-backend"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "/ecs/${var.name_prefix}-backend"
  }
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/${var.name_prefix}-frontend"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "/ecs/${var.name_prefix}-frontend"
  }
}

# ===========================================================================
# シークレット / 設定（§10）
#   JWT_SECRET    → Secrets Manager（値はここで生成しリポジトリに残さない）
#   Redis 接続情報 → SSM Parameter Store（data の Redis エンドポイントから）
# ===========================================================================
resource "random_password" "jwt_secret" {
  length = 48
  # HS256 は 32 バイト以上が要件。記号なしの英数で十分な長さを確保する。
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name = "${var.name_prefix}-jwt-secret"
  # author-only: destroy → 再 apply の名前衝突を避けるため復旧猶予を 0（即時削除）にする。
  recovery_window_in_days = 0

  tags = {
    Name = "${var.name_prefix}-jwt-secret"
  }
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt_secret.result
}

resource "aws_ssm_parameter" "redis_host" {
  name  = "/${var.name_prefix}/redis/host"
  type  = "String"
  value = var.redis_host

  tags = {
    Name = "${var.name_prefix}-redis-host"
  }
}

resource "aws_ssm_parameter" "redis_port" {
  name  = "/${var.name_prefix}/redis/port"
  type  = "String"
  value = tostring(var.redis_port)

  tags = {
    Name = "${var.name_prefix}-redis-port"
  }
}

# ===========================================================================
# IAM（§10）
#   実行ロール: ECS エージェントが ECR pull / Logs 書込 / シークレット取得に使う
#   タスクロール: アプリ自身が S3 へアクセスするのに使う（静的キーを持たせない）
# ===========================================================================
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# --- 実行ロール -------------------------------------------------------------
resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${var.name_prefix}-ecs-execution"
  }
}

# ECR pull / CloudWatch Logs への書き込みを許可する AWS マネージドポリシー。
resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# secrets でのシークレット注入はマネージドポリシーに含まれないので個別 ARN に絞って付与する。
data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.jwt.arn, var.db_master_user_secret_arn]
  }
  statement {
    sid       = "ReadSsmParams"
    actions   = ["ssm:GetParameters"]
    resources = [aws_ssm_parameter.redis_host.arn, aws_ssm_parameter.redis_port.arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.name_prefix}-ecs-execution-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# --- タスクロール（S3 アクセス）-------------------------------------------
resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${var.name_prefix}-ecs-task"
  }
}

# アバター / 添付バケットのオブジェクト操作のみに絞る（最小権限・§10）。
data "aws_iam_policy_document" "task_s3" {
  statement {
    sid       = "ObjectRW"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${var.s3_bucket}/*"]
  }
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.s3_bucket}"]
  }
}

resource "aws_iam_role_policy" "task_s3" {
  name   = "${var.name_prefix}-ecs-task-s3"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_s3.json
}

# ===========================================================================
# ALB（§3 / §6.1）— 外部公開点。public subnet に置き WSS/443 を終端する。
# ===========================================================================
resource "aws_lb" "this" {
  name               = "${var.name_prefix}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [var.alb_sg_id]
  subnets            = var.public_subnet_ids

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

# target_type=ip: Fargate(awsvpc) のタスク ENI を直接ターゲットにする。
# スティッキー（lb_cookie）で WebSocket の同一クライアントを同一タスクへ寄せる（§6.1）。
resource "aws_lb_target_group" "backend" {
  name        = "${var.name_prefix}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  stickiness {
    type            = "lb_cookie"
    enabled         = true
    cookie_duration = 86400
  }

  tags = {
    Name = "${var.name_prefix}-tg"
  }
}

# フロントエンド（Next.js standalone, port 3000）の target group。
# SSR はステートレスでセッションアフィニティ不要なのでスティッキーは張らない。
# ヘルスチェックはアプリ状態に依存しない /healthz（200 固定）を使う。
resource "aws_lb_target_group" "frontend" {
  name        = "${var.name_prefix}-fe-tg"
  port        = var.frontend_container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/healthz"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.name_prefix}-fe-tg"
  }
}

# HTTPS:443 — 証明書がある時だけ張る（WSS 終端）。default はフロントへ。
resource "aws_lb_listener" "https" {
  count             = local.https_enabled ? 1 : 0
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# HTTP:80 — 証明書ありなら 443 へリダイレクト、なしなら default をフロントへ直接転送（フォールバック）。
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.https_enabled ? "redirect" : "forward"
    target_group_arn = local.https_enabled ? null : aws_lb_target_group.frontend.arn

    dynamic "redirect" {
      for_each = local.https_enabled ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

# 実際にトラフィックを処理するリスナー（https があればそちら、なければ http）。
# バックエンド向けパスルールはここに張る（http→https リダイレクト時は 443 側で評価される）。
# https は count で 0/1 個になるため one() で取り出し、無ければ http にフォールバックする
# （三項の index 直アクセスは count=0 時に評価エラーになり得るため避ける）。
locals {
  serving_listener_arn = coalesce(one(aws_lb_listener.https[*].arn), aws_lb_listener.http.arn)
}

# REST（/api/*）と WebSocket（/ws・/ws/*）だけをバックエンドへ振り、それ以外（= 画面）は
# default のフロントへ流す。SockJS は /ws 配下に複数パスを使うためワイルドカードも含める。
resource "aws_lb_listener_rule" "backend" {
  listener_arn = local.serving_listener_arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    path_pattern {
      values = ["/api/*", "/ws", "/ws/*"]
    }
  }
}

# ===========================================================================
# ECS Fargate（§3 / §6.1）— private subnet に複数タスクを Multi-AZ 配置する。
# ===========================================================================
resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "${var.name_prefix}-cluster"
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.name_prefix}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = local.container_name
    image     = local.image
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = local.container_env

    # 機密値はイメージにも state にも置かず、起動時に Secrets Manager / SSM から注入する（§10）。
    # DB は RDS マネージドシークレットの JSON キーを「:<key>::」で個別に取り出す。
    secrets = [
      { name = "DB_USERNAME", valueFrom = "${var.db_master_user_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.db_master_user_secret_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
      { name = "REDIS_HOST", valueFrom = aws_ssm_parameter.redis_host.arn },
      { name = "REDIS_PORT", valueFrom = aws_ssm_parameter.redis_port.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "backend"
      }
    }
  }])

  tags = {
    Name = "${var.name_prefix}-backend"
  }
}

resource "aws_ecs_service" "backend" {
  name            = "${var.name_prefix}-backend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # private subnet 配置・public IP なし。ECR/Logs への egress は NAT 経由（E2E 時のみ有効化）。
  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = local.container_name
    container_port   = var.container_port
  }

  # Spring Boot の起動に時間がかかるため、ヘルスチェック失敗での即時 kill を猶予する。
  health_check_grace_period_seconds = 120

  # CD（§7）がサービス更新でタスク定義を差し替える分は Terraform で巻き戻さない。
  lifecycle {
    ignore_changes = [task_definition]
  }

  # default はフロントへ向くため、バックエンドは path ルール経由でのみ届く。
  depends_on = [aws_lb_listener_rule.backend]
}

# --- フロントエンド（Next.js standalone）---------------------------------
# 機密注入なし。NEXT_PUBLIC_* はビルド時にバンドルへ焼くため runtime env は最小。
# standalone の server.js は PORT / HOSTNAME を見る（Dockerfile で設定済みだが明示しておく）。
resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.name_prefix}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_task_cpu
  memory                   = var.frontend_task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = local.frontend_container_name
    image     = local.frontend_image
    essential = true

    portMappings = [{
      containerPort = var.frontend_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "NODE_ENV", value = "production" },
      { name = "PORT", value = tostring(var.frontend_container_port) },
      { name = "HOSTNAME", value = "0.0.0.0" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.frontend.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "frontend"
      }
    }
  }])

  tags = {
    Name = "${var.name_prefix}-frontend"
  }
}

resource "aws_ecs_service" "frontend" {
  name            = "${var.name_prefix}-frontend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.ecs_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = local.frontend_container_name
    container_port   = var.frontend_container_port
  }

  # Next standalone は起動が速いが、初回 pull/起動の猶予を少し取る。
  health_check_grace_period_seconds = 60

  lifecycle {
    ignore_changes = [task_definition]
  }

  # default アクションがフロント TG を指すので、リスナー本体に依存させる。
  depends_on = [aws_lb_listener.http]
}

# ===========================================================================
# SG 経路（フロント分）— ALB SG から ECS タスクの 3000 を許可する。
# network module の ecs_sg は 8080（backend）のみ開けているため、frontend 用に
# このモジュールで 3000 の ingress を追加する（フロントを ECS でホストする Step5 の都合）。
# ===========================================================================
resource "aws_vpc_security_group_ingress_rule" "ecs_frontend_from_alb" {
  security_group_id            = var.ecs_sg_id
  description                  = "frontend port ${var.frontend_container_port} from ALB SG"
  referenced_security_group_id = var.alb_sg_id
  from_port                    = var.frontend_container_port
  to_port                      = var.frontend_container_port
  ip_protocol                  = "tcp"
}
