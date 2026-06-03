# RaiseChat 運用 Runbook

RaiseChat の AWS インフラを **実際に操作するときの通し台本**。ゼロから本番を立て、デプロイし、
監視・障害対応し、最後に潰すまでを上から順に叩ける形でまとめる。

- **設計の「なぜ」**は [docs/infrastructure.md](../docs/infrastructure.md) を正とする。
- **各スタックの部品説明**は [infra/terraform/README.md](terraform/README.md) /
  [infra/ansible/README.md](ansible/README.md) を参照。
- 本書はそれらを横断した **「手を動かす順番」** に責務を絞る。

---

## 0. 位置づけ・前提

### 0.1 課金スタンス（最重要）

本プロジェクトは **author-only / オンデマンド apply**（[infrastructure.md §12.1](../docs/infrastructure.md)）。

- NAT Gateway・ALB・Fargate・RDS・ElastiCache は **存在するだけで時間課金**される。
- 学習・CI は `terraform validate` まで（無課金）。実リソースは **E2E 検証のときだけ apply し、
  終わったら必ず `destroy` する**。
- 常駐させない。「立てっぱなしで寝る」が一番の事故。**§6 の撤収を必ず最後に実行**する。

### 0.2 環境の主要な固定値

| 項目 | 値 | 出どころ |
| --- | --- | --- |
| リージョン | `ap-northeast-1` | [dev.tfvars.example](terraform/envs/dev/dev.tfvars.example) |
| 命名接頭辞 `name_prefix` | `raisechat-dev` | [locals.tf](terraform/envs/dev/locals.tf) |
| state バケット | `raisechat-tfstate` | [backend.tf](terraform/envs/dev/backend.tf) / bootstrap |
| state ロックテーブル | `raisechat-tflock` | 同上 |
| ECS クラスタ | `raisechat-dev-cluster` | name_prefix + `-cluster` |
| backend サービス/ECR/taskdef | `raisechat-dev-backend` | 同名で統一 |
| frontend サービス/ECR/taskdef | `raisechat-dev-frontend` | 同名で統一 |
| アラート SNS トピック | `raisechat-dev-alerts` | [monitoring](terraform/modules/monitoring/) |
| ダッシュボード | `raisechat-dev-overview` | 同上 |

> リソース名やエンドポイントは原則 `terraform output`（§2.4）で引く。本書のコマンド例の
> `$(terraform output -raw ...)` はそのための定型。

---

## 1. 事前準備

実 AWS を触る作業（§2 以降）の前に、ローカルに以下を揃える。§0.1 のとおり apply 前の検証だけなら不要。

### 1.1 ツール

| ツール | 用途 | 確認コマンド |
| --- | --- | --- |
| Terraform | IaC（versions.tf でピン留め） | `terraform version` |
| AWS CLI v2 | apply / 運用操作 | `aws --version` |
| Session Manager plugin | bastion への SSM 接続（§5） | `session-manager-plugin` |
| Docker | イメージの build / push（手動時） | `docker version` |
| jq | output やタスク定義の整形 | `jq --version` |

Session Manager plugin が無い場合（macOS）:

```bash
brew install --cask session-manager-plugin
```

### 1.2 AWS 認証情報

```bash
aws configure        # アクセスキー / リージョン(ap-northeast-1) を設定
aws sts get-caller-identity   # 自分の Account / ARN が返れば OK
```

- apply にはネットワーク・RDS・ECS・IAM・SSM 等を作る広い権限が要る（学習用途では管理者相当を想定）。
- CD（GitHub Actions）は静的キーではなく **OIDC** で短命トークンを使う（§3.1）。ローカルの上記
  認証情報は **手動 apply と運用操作のため**のもの。

---

## 2. 初回構築の台本（＝実 AWS apply E2E）

> ここが「実 AWS への apply E2E 検証」の台本そのもの。**§6 の撤収までを 1 セッションでやり切る**前提で進める。

### 2.1 state backend を用意（初回のみ・ほぼ無料）

リモート state 置き場（S3 + DynamoDB）を bootstrap スタックで作る。1 回作れば以降は再利用。

```bash
cd infra/terraform/bootstrap
terraform init                 # local state で初期化
terraform apply                # raisechat-tfstate / raisechat-tflock を作成（ほぼ無料）
terraform output               # bucket / table / region を確認
```

出力された `state_bucket_name` / `lock_table_name` / `aws_region` が
[envs/dev/backend.tf](terraform/envs/dev/backend.tf) の値（`raisechat-tfstate` / `raisechat-tflock` /
`ap-northeast-1`）と一致していることを確認する。違う場合は backend.tf を合わせる。

### 2.2 dev 環境を backend 付きで初期化

```bash
cd ../envs/dev
terraform init                 # S3 backend へ初期化（以降 state はリモート管理）
```

### 2.3 変数ファイルを用意し、課金リソースを ON にする

```bash
cp dev.tfvars.example dev.tfvars   # dev.tfvars は .gitignore 済み
```

`dev.tfvars` を編集し、**E2E 検証のときだけ**以下を有効化する（既定は全部 OFF＝無課金寄り）:

```hcl
enable_nat_gateway = true   # private の ECS が ECR/Logs へ出るために必須
# 冗長度を検証するなら（任意・課金増）:
# db_multi_az              = true
# redis_num_cache_clusters = 2
```

> `container_image` / `frontend_container_image` を空のままにすると ECS は各 ECR の `:latest` を
> 使う。**先にイメージを push（§2.5）してから apply** するのが安全。未 push のまま apply すると
> タスクが pull 失敗で立ち上がらない。

### 2.4 plan で差分を確認

```bash
terraform plan -var-file=dev.tfvars
```

作成予定リソース（VPC / subnet / SG / NAT / ALB / ECS / RDS / ElastiCache / ECR / 監視 / bastion）を確認。

### 2.5 イメージを先に push（ECR 作成だけ先行 apply する手順）

ECS が pull できるよう、ECR にイメージを置いてから本 apply する。ECR リポジトリは ecs module が作るので、
**先に ECR だけ apply → push → 残りを apply** の順にする。

```bash
# 1) ECR リポジトリだけ先に作る（バックエンド/フロント）
terraform apply -var-file=dev.tfvars \
  -target=module.ecs.aws_ecr_repository.backend \
  -target=module.ecs.aws_ecr_repository.frontend

# 2) ログインしてビルド & push
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT}.dkr.ecr.ap-northeast-1.amazonaws.com"
aws ecr get-login-password --region ap-northeast-1 \
  | docker login --username AWS --password-stdin "$REGISTRY"

docker build -t "$REGISTRY/raisechat-dev-backend:latest"  ./backend
docker push    "$REGISTRY/raisechat-dev-backend:latest"
docker build -t "$REGISTRY/raisechat-dev-frontend:latest" ./frontend
docker push    "$REGISTRY/raisechat-dev-frontend:latest"
```

> ECR リソースの正確なアドレスは `terraform state list | grep ecr` で確認できる。target 名が違う場合は
> その出力に合わせる。

### 2.6 本 apply

```bash
terraform apply -var-file=dev.tfvars     # ★ ここから本格的に課金が始まる
```

### 2.7 疎通確認

```bash
terraform output                          # 主要な出力を一覧
ALB=$(terraform output -raw alb_dns_name)
curl -i "http://$ALB/actuator/health"     # backend ヘルス（{"status":"UP"} 期待）
curl -i "http://$ALB/"                     # フロント（Next.js）が返るか
```

ブラウザで `http://<alb_dns_name>/` を開き、ログイン〜チャットの主要動線（WebSocket 接続含む）を確認する。

- タスクが上がらない場合は §7 のトラブルシュートと §4 の CloudWatch Logs を見る。
- `certificate_arn` 未指定なら HTTP:80 フォールバック。本番想定で HTTPS を見るなら ACM 証明書を用意して
  `certificate_arn` を設定し再 apply（WSS は 443 終端）。

### 2.8 検証が済んだら撤収（§6 へ）

**ここで放置しない。** 確認が取れたら直ちに §6 の `destroy` に進む。

---

## 3. 通常デプロイ運用

apply 済み（ECS クラスタが存在する）前提。コードを ECS に反映する日常運用。
仕組みは [.github/workflows/deploy.yml](../.github/workflows/deploy.yml) / [infrastructure.md §7](../docs/infrastructure.md)。

### 3.1 初回だけ：OIDC デプロイロールを GitHub に登録

```bash
cd infra/terraform/envs/dev
terraform output -raw deploy_role_arn       # cicd module が発行したロール ARN
```

この ARN を GitHub の **Settings → Secrets and variables → Actions → Variables** に
リポジトリ変数 `AWS_DEPLOY_ROLE_ARN` として登録する（Secret ではなく Variable）。

> ロールは main 限定・最小権限（ECR push / ECS UpdateService / PassRole）。静的アクセスキーは置かない。

### 3.2 自動デプロイ（通常運用）

- `main` への push で、変更パスに応じて backend / frontend を自動デプロイ。
  - 起動条件: `backend/**` / `frontend/**` / `deploy.yml` の変更。
- 流れ: build → `:<commit SHA>` と `:latest` で ECR push → 現行タスク定義の image だけ差し替えて新リビジョン登録
  → ローリング更新（`wait-for-service-stability`）。
- main 直 push は禁止（[CLAUDE.md](../CLAUDE.md)）。実体は **PR マージ後の main** が起点。

### 3.3 手動デプロイ（任意のタイミング）

GitHub の **Actions → "Deploy to AWS (ECR / ECS)" → Run workflow** から起動。

- `target` を `both` / `backend` / `frontend` から選ぶ。
- apply 直後にコード変更なしで一度デプロイを流したいときにも使う。

### 3.4 ロールバック

CD は SHA 指定の入力を持たないため、ロールバックは **ECS のタスク定義リビジョンを前に戻す**のが速い。
ECS サービスは Terraform 側で `lifecycle.ignore_changes=[task_definition]` 済みなので、手動リビジョン
切り替えは Terraform 管理と競合しない。

```bash
CLUSTER=raisechat-dev-cluster
SVC=raisechat-dev-backend          # frontend をロールバックするなら raisechat-dev-frontend

# 直近のリビジョン一覧（新しい順）
aws ecs list-task-definitions --family-prefix "$SVC" --sort DESC --max-items 5

# 1つ前の正常なリビジョンへ即時切り替え（例: :7 に戻す）
aws ecs update-service --cluster "$CLUSTER" --service "$SVC" \
  --task-definition "$SVC:7" --force-new-deployment

# 安定するまで待つ
aws ecs wait services-stable --cluster "$CLUSTER" --services "$SVC"
```

> コードごと戻したい場合は、GitHub で当該コミットを `git revert` → main にマージすれば、正常時のコードが
> 再ビルド・再デプロイされる。緊急時はまず上の即時リビジョン切り替えで止血し、その後 revert で整える。

---

## 4. 監視・アラート対応

実体は [infra/terraform/modules/monitoring](terraform/modules/monitoring/) /
[infrastructure.md §9](../docs/infrastructure.md)。

### 4.1 ダッシュボードを開く

```bash
terraform output -raw dashboard_name        # raisechat-dev-overview
```

AWS コンソール → **CloudWatch → Dashboards → `raisechat-dev-overview`**。
ECS CPU/メモリ、RunningTaskCount、ALB の Healthy/UnHealthy ホスト、リクエスト数/5xx、レスポンスタイムが 1 枚で見える。

### 4.2 初回だけ：アラート通知先を購読する

SNS トピックは Terraform が作るが、**通知先（宛先）は state・コードに残さない方針**のため apply 後に手動で購読を足す。

```bash
TOPIC=$(terraform output -raw alerts_sns_topic_arn)

# メールで受け取る場合（確認メールのリンクをクリックして承認する）
aws sns subscribe --topic-arn "$TOPIC" --protocol email --notification-endpoint you@example.com

# Slack 等に流す場合は AWS Chatbot もしくは Lambda/Webhook を別途構成して購読する
```

### 4.3 アラーム一覧と一次対応

しきい値は変数（`cpu_high_threshold` 等）。`treat_missing_data` の挙動に注意。

| アラーム | 条件 | まず疑うこと / 一次対応 |
| --- | --- | --- |
| `*-ecs-cpu-high` | CPU 使用率がしきい値超過 | 負荷スパイクかリーク。ダッシュボードで継続性を見て、必要なら `ecs_desired_count` を上げて再 apply、もしくは一時的に `update-service --desired-count` |
| `*-ecs-memory-high` | メモリ使用率がしきい値超過 | メモリリーク/不足。`ecs_task_memory` 見直し。ログで OOM を確認 |
| `*-ecs-running-task-low` | 稼働タスク数 < desired（欠損は breaching） | **タスクが立ち上がれていない**。`describe-services` の events と CloudWatch Logs を確認（§4.4）。pull 失敗・ヘルスチェック不通が定番 |
| `*-alb-unhealthy-hosts` | TG に異常ホスト ≥ 1 | アプリが `/actuator/health` を返せていない。アプリログ・SG・ターゲット登録を確認 |
| `*-alb-5xx-high` | ターゲット由来 5xx がしきい値超過 | アプリ例外か依存先（DB/Redis）障害。アプリログで例外を特定。DB/Redis 接続を §5 で確認 |

### 4.4 ログを見る

```bash
terraform output -raw log_group_name        # アプリログの CloudWatch Logs グループ
# 直近ログを tail（aws cli v2）
aws logs tail "$(terraform output -raw log_group_name)" --since 15m --follow
```

サービスの直近イベント（タスクが落ちる理由が出る）:

```bash
aws ecs describe-services --cluster raisechat-dev-cluster \
  --services raisechat-dev-backend \
  --query 'services[0].events[:10].message' --output table
```

---

## 5. bastion 経由で DB / Redis に接続

RDS / ElastiCache は private のため直接は届かない。運用 bastion（EC2）を **SSM Session Manager**（インバウンド
SSH なし）で踏み台にする。実体は [modules/bastion](terraform/modules/bastion/) と
[infra/ansible](ansible/README.md)。

### 5.1 初回だけ：bastion の中身を Ansible で整える

bastion の「存在」は Terraform が作る。「中身」（psql / redis6-cli / nc / jq の導入・MOTD）は Ansible で入れる。

```bash
cd infra/ansible
ansible-galaxy collection install -r requirements.yml

# inventory/hosts を用意（ansible_aws_ssm_instance_id に Terraform の bastion_instance_id を入れる）
cp inventory/hosts.example inventory/hosts
# bastion_instance_id は: terraform -chdir=../terraform/envs/dev output -raw bastion_instance_id

ansible-playbook -i inventory/hosts playbook.yml
```

> ローカルでの role 検証は molecule（Docker・無課金）で完結する（[ansible/README.md](ansible/README.md)）。
> 実 bastion への適用は apply 済みのときだけ。

### 5.2 bastion にログイン

```bash
cd infra/terraform/envs/dev
eval "$(terraform output -raw ssm_start_session_command)"
# 実体は: aws ssm start-session --target <bastion_instance_id>
```

ログインすると MOTD に接続例（psql / redis6-cli / nc）が表示される。

### 5.3 DB に接続（パスワードは Secrets Manager から取得）

RDS マスターパスワードは Secrets Manager 管理。コード・state に平文は無い。

```bash
# ローカル側で接続情報を引く
SECRET_ARN=$(terraform output -raw db_master_user_secret_arn)
aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" \
  --query SecretString --output text | jq .     # username / password が入っている

terraform output -raw db_instance_endpoint        # host:port
terraform output -raw db_name                      # 初期 DB 名
```

bastion セッション内で:

```bash
psql -h <rds_endpoint_host> -p 5432 -U <username> -d raisechat
# Redis:
redis6-cli -h <redis_endpoint> -p 6379 PING       # PONG が返れば疎通
# 純粋な疎通確認だけなら:
nc -vz <host> <port>
```

> bastion は使い終わったらセッションを閉じる（`exit`）。常駐 EC2 自体の課金が気になる場合は、
> 検証セッションごと §6 で destroy する構成のため、撤収時に一緒に消える。

---

## 6. 撤収・課金停止（最重要）

§2 で立てたものを潰し、課金を止める。**E2E 検証は必ずここまでやり切る。**

### 6.1 destroy

```bash
cd infra/terraform/envs/dev
terraform destroy -var-file=dev.tfvars
```

> ECR にイメージが残っていると repository の削除が失敗することがある。その場合はイメージを消してから再実行:
> `aws ecr batch-delete-image --repository-name raisechat-dev-backend --image-ids imageTag=latest`（frontend も同様）。

### 6.2 残存リソースの確認（destroy 漏れチェック）

「消したつもりで課金が続く」を防ぐため、主要なものが消えたか確認する。

```bash
# 立っていないことを確認（空 or 該当なしが期待）
aws ecs list-clusters        --query "clusterArns[?contains(@,'raisechat')]"
aws elbv2 describe-load-balancers --query "LoadBalancers[?contains(LoadBalancerName,'raisechat')].LoadBalancerName"
aws rds describe-db-instances    --query "DBInstances[?contains(DBInstanceIdentifier,'raisechat')].DBInstanceIdentifier"
aws elasticache describe-cache-clusters --query "CacheClusters[?contains(CacheClusterId,'raisechat')].CacheClusterId"
aws ec2 describe-nat-gateways    --filter Name=state,Values=available \
  --query "NatGateways[].NatGatewayId"          # NAT は時間課金。残っていたら要対処
aws ec2 describe-instances --filters Name=instance-state-name,Values=running \
  --query "Reservations[].Instances[?contains(Tags[?Key=='Project']|[0].Value,'raise')].InstanceId"
```

何か残っていたら `terraform destroy` を再実行するか、コンソールから手動削除する。

### 6.3 bootstrap の扱い

state バケット（`raisechat-tfstate`）と DynamoDB（`raisechat-tflock`）は **ほぼ無料**なので、
次回の apply のために**残しておいてよい**。完全に片付けるなら最後に bootstrap も destroy する。

```bash
cd ../../bootstrap
terraform destroy        # state 置き場ごと消す（次回はまた §2.1 から）
```

---

## 7. トラブルシュート

| 症状 | 原因 | 対処 |
| --- | --- | --- |
| `deploy.yml` が失敗（ECS not found 等） | infra 未 apply（クラスタ無し） | 先に §2 で apply。apply 前は CD を走らせない |
| `terraform init` で backend 不一致エラー | backend.tf の bucket/table と bootstrap 出力がズレ | §2.1 で output を確認し backend.tf を一致させる。`-reconfigure` で再初期化 |
| ECS タスクが pull 失敗で上がらない | イメージ未 push / NAT 無効 | §2.5 で push 済みか、`enable_nat_gateway=true` か確認 |
| タスクは起動するが unhealthy | `/actuator/health` 不通・SG | アプリログ（§4.4）。backend SG が ALB から 8080 を受けているか |
| `aws ssm start-session` で繋がらない | SSM plugin 未導入 / IAM 権限 / SSM Agent | §1.1 で plugin、bastion の IAM(SSM) ロール、エージェント稼働を確認 |
| CD で `Could not assume role` | `AWS_DEPLOY_ROLE_ARN` 未設定/誤り | §3.1 でリポジトリ Variable を再確認（Secret ではなく Variable） |
| destroy が ECR で失敗 | リポジトリにイメージが残存 | §6.1 の注記でイメージ削除後に再 destroy |

---

## 付録：よく使う output 一覧

```bash
cd infra/terraform/envs/dev
terraform output                      # 全部
terraform output -raw alb_dns_name              # E2E の接続先
terraform output -raw ecr_repository_url        # backend ECR
terraform output -raw frontend_ecr_repository_url
terraform output -raw deploy_role_arn           # CD の OIDC ロール
terraform output -raw bastion_instance_id       # SSM --target
terraform output -raw db_master_user_secret_arn # DB パスワードの Secrets Manager ARN
terraform output -raw alerts_sns_topic_arn      # アラート購読先
terraform output -raw dashboard_name            # CloudWatch ダッシュボード
terraform output -raw log_group_name            # アプリログ
```
