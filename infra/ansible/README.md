# infra/ansible

RaiseChat の構成管理（⑤Step8）。[`modules/bastion`](../terraform/modules/bastion/README.md) で
立てた運用 bastion の **OS / ミドルウェア構成を Ansible で管理**する
（Terraform = bastion の「存在」、Ansible = bastion の「中身」）。
設計の正は [docs/infrastructure.md §8](../../docs/infrastructure.md)。

## 構成

```
infra/ansible/
  ansible.cfg              # roles_path / インベントリ等
  requirements.yml         # amazon.aws / community.aws / community.docker
  playbook.yml             # サイト playbook（hosts: bastion, become）
  inventory/hosts.example  # SSM 接続インベントリの例
  roles/bastion/
    defaults/main.yml      # TZ / アップグレード可否 / 導入パッケージ
    tasks/main.yml         # TZ・パッケージ更新・クライアント導入・MOTD
    meta/main.yml
    molecule/default/      # molecule + Docker（amazonlinux:2023）
```

## ロールがやること（`roles/bastion`）

- システムタイムゾーンを `Asia/Tokyo` に（`/etc/localtime` のリンク。timedatectl 非依存）
- インストール済みパッケージの更新（`bastion_upgrade_all` で切替）
- 踏み台クライアントの導入: `postgresql15`（psql）/ `redis6`（redis6-cli）/ `nmap-ncat`（nc）/ `jq`
- 利用ガイドの MOTD 配置（RDS / Redis への接続例）

## ローカル検証（molecule + Docker・無課金）

クラウドに触れず、Docker コンテナ（`amazonlinux:2023`）に対してロールを実行して検証する。

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install "molecule" "molecule-plugins[docker]" "ansible-lint" "docker"
ansible-galaxy collection install -r requirements.yml

cd roles/bastion
molecule test          # lint → create → converge → idempotence → verify → destroy
```

> `.venv` はコミットしない（`.gitignore` 済み）。`molecule test` は冪等性まで検証する
> （converge を 2 回流して 2 回目に変更が出ないこと）。

## 実 bastion への適用（apply 後）

実機は SSH を開けない（SSM 接続）。`community.aws.aws_ssm` connection plugin で接続する。

1. AWS CLI ＋ Session Manager plugin をローカルに用意
2. ファイル転送用 S3 バケットを 1 つ用意（`aws_ssm` はファイル転送に S3 を使う）
3. `inventory/hosts.example` を埋める（`ansible_aws_ssm_instance_id` は Terraform の
   `bastion_instance_id` 出力）
4. 適用:

```bash
ansible-galaxy collection install -r requirements.yml
ansible-playbook -i inventory/hosts playbook.yml
```

## 方針

author-only / オンデマンド。検証は molecule（ローカル Docker・無課金）まで。実 bastion への
apply は運用時のみ。
