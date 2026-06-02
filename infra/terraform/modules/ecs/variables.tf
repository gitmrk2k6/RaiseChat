variable "name_prefix" {
  description = "リソース名の接頭辞（呼び出し元の local.name_prefix。例: raisechat-dev）"
  type        = string
}

# --- network module から受け取る配線情報 -----------------------------------
variable "vpc_id" {
  description = "ALB の target group を置く VPC ID（network module の vpc_id）"
  type        = string
}

variable "public_subnet_ids" {
  description = "ALB を置く public subnet の ID 一覧（network module の出力）。Multi-AZ に備え 2 つ以上"
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "public_subnet_ids は ALB の Multi-AZ 配置に備え 2 つ以上にする（infrastructure.md §5）。"
  }
}

variable "private_subnet_ids" {
  description = "ECS Fargate タスクを置く private subnet の ID 一覧（network module の出力）。Multi-AZ に備え 2 つ以上"
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "private_subnet_ids は ECS の Multi-AZ 配置に備え 2 つ以上にする（infrastructure.md §6.1）。"
  }
}

variable "alb_sg_id" {
  description = "ALB にアタッチする SG ID（network module の alb_sg_id。インターネットから 443/80 を受ける）"
  type        = string
}

variable "ecs_sg_id" {
  description = "ECS タスクにアタッチする SG ID（network module の ecs_sg_id。ALB SG からのみ 8080 を受ける）"
  type        = string
}

# --- data module（Step3）から受け取る配線情報 ------------------------------
variable "db_endpoint_host" {
  description = "RDS のホスト名（ポートを含まない。data module の db_instance_address）。SPRING_DATASOURCE_URL の組み立てに使う"
  type        = string
}

variable "db_port" {
  description = "RDS のポート（data module の db_instance_port）"
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "接続先データベース名（data module の db_name）"
  type        = string
  default     = "raisechat"
}

variable "db_master_user_secret_arn" {
  description = <<-EOT
    RDS マスターパスワードを保管する Secrets Manager シークレットの ARN（data module の出力）。
    タスク定義の secrets で username / password を JSON キー指定で注入する（§10）。
  EOT
  type        = string
}

variable "redis_host" {
  description = "Redis プライマリエンドポイントのホスト名（data module の redis_primary_endpoint_address）。SSM Parameter Store 経由で注入する"
  type        = string
}

variable "redis_port" {
  description = "Redis のポート（data module の redis_port）"
  type        = number
  default     = 6379
}

# --- アプリ設定（タスク定義の environment） --------------------------------
variable "s3_bucket" {
  description = <<-EOT
    アバター（F-02）・添付（F-10）を置く S3 バケット名。タスクロールの権限スコープと
    アプリの S3_BUCKET 環境変数に使う。バケット本体の作成は本モジュール外（実 S3 を別途用意 or 後続 storage）。
  EOT
  type        = string
  default     = "raisechat-avatars"
}

variable "ws_allowed_origins" {
  description = <<-EOT
    WebSocket / REST CORS で許可するオリジン（WS_ALLOWED_ORIGINS / APP_CORS_ALLOWED_ORIGINS）。
    空文字なら注入せずアプリ既定（localhost:3000）に委ねる。本番ではフロントのドメインを渡す。
  EOT
  type        = string
  default     = ""
}

variable "invite_base_url" {
  description = "招待リンクのベース URL（INVITE_BASE_URL）。空文字なら注入せずアプリ既定に委ねる"
  type        = string
  default     = ""
}

# --- コンテナ / サイジング --------------------------------------------------
variable "container_image" {
  description = <<-EOT
    ECS タスクが起動するコンテナイメージ（例 <ecr>/<repo>:<tag>）。
    空文字なら本モジュールが作る ECR リポジトリの :latest を使う（初回は push 後に apply する想定）。
  EOT
  type        = string
  default     = ""
}

variable "container_port" {
  description = "アプリのリッスンポート（Spring Boot 8080）。target group / SG 経路と一致させる"
  type        = number
  default     = 8080
}

variable "cpu_architecture" {
  description = "Fargate の CPU アーキテクチャ。push するイメージのビルド先と一致させる（X86_64 / ARM64）"
  type        = string
  default     = "X86_64"
}

variable "task_cpu" {
  description = "タスクの CPU ユニット（256/512/1024…）。MVP は 512 から"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "タスクのメモリ（MiB）。task_cpu と整合する組み合わせにする。MVP は 1024 から"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "ECS サービスの希望タスク数。§6.1「2 タスク以上を基本」に従い既定 2（Multi-AZ 分散）"
  type        = number
  default     = 2

  validation {
    condition     = var.desired_count >= 1
    error_message = "desired_count は 1 以上にする。"
  }
}

# --- ALB TLS（cert トグル） -------------------------------------------------
variable "certificate_arn" {
  description = <<-EOT
    ALB の HTTPS:443 リスナーに使う ACM 証明書 ARN（§3 WSS/443 終端）。
    指定すると HTTPS:443 を張り HTTP:80 は 443 へリダイレクトする。
    空文字なら HTTP:80 を直接 target group へ転送する（ドメイン未整備でも E2E できる author-only 用フォールバック）。
  EOT
  type        = string
  default     = ""
}

# --- ログ -------------------------------------------------------------------
variable "log_retention_days" {
  description = "CloudWatch Logs の保持日数（§9）。dev は短め"
  type        = number
  default     = 14
}
