# RaiseChat

Slack 風チャット Web アプリケーション。RaiseTech AI エンジニアコース **上級編** の課題として、WebSocket リアルタイム通信 / Redis キャッシュ戦略 / 冗長化構成 / 自動デプロイ / Claude Code 自動コードレビュー といった上級編テーマを実装で身につけることを目的とする。

---

## プロジェクト概要

業務利用を前提に、Slack の中核体験（チャンネル＋スレッド＋全文検索＋絵文字リアクション＋メンション）をミニマムに再現する。Bot 連携やボイスチャンネル等の周辺機能はスコープ外とする。

「Slack 風」と発注された際にお客様が期待しているもの、他のビジネスチャットツール（Microsoft Teams / Chatwork / LINE WORKS / Google Chat / Discord / Mattermost）との比較、機能スコープの判断根拠は [docs/why-slack.md](docs/why-slack.md) に整理している。

---

## ドキュメント

| ドキュメント | 内容 |
| --- | --- |
| [docs/requirements.md](docs/requirements.md) | 要件定義書（ハブ）。機能要件・非機能要件・スコープ外 |
| [docs/functional-requirements.md](docs/functional-requirements.md) | 機能要件書。F-01〜F-16 の機能定義・バリデーション・ユースケース |
| [docs/why-slack.md](docs/why-slack.md) | 「Slack 風」発注意図の解釈と競合比較・スコープ判断の根拠 |
| `docs/screen-design.md` | 画面設計書（設計フェーズで作成予定） |
| `docs/database-design.md` | データベース設計書（設計フェーズで作成予定） |
| `docs/tech-stack.md` | 技術スタック詳細（設計フェーズで作成予定） |
| `docs/infrastructure.md` | インフラ構成（設計フェーズ〜実装フェーズで作成予定） |
| `docs/realtime-design.md` | WebSocket / STOMP / Redis Pub-Sub 設計（設計フェーズで作成予定） |
| `docs/cache-strategy.md` | Redis キャッシュ戦略（設計フェーズで作成予定） |
| [CLAUDE.md](CLAUDE.md) | Claude Code 利用時のルール（命名規則・GitHub フロー・ポート） |

---

## 機能一覧（MVP）

詳細は [docs/functional-requirements.md](docs/functional-requirements.md) を参照。

| 機能 | 概要 |
| --- | --- |
| F-01 ユーザー認証 | ユーザー ID + パスワードで登録・ログイン・ログアウト・JWT 認証 |
| F-02 プロフィール管理 | アバター画像・表示名・ステータスメッセージの編集 |
| F-03 ワークスペース管理 | ワークスペース新規作成・参加・切り替え |
| F-04 チャンネル管理 | パブリック / プライベートチャンネル作成・参加・退出 |
| F-05 チャンネルメッセージ | チャンネル内テキスト投稿（WebSocket リアルタイム配信） |
| F-06 ダイレクトメッセージ | 1 対 1 の DM |
| F-07 メッセージ編集・削除 | 自分のメッセージの編集・削除 |
| F-08 スレッド | メッセージへの返信。独立スレッドビュー |
| F-09 マークダウン記法 | 太字・コード・リスト・引用などのレンダリング |
| F-10 ファイル添付 | 画像・動画ファイル添付（S3 想定） |
| F-11 絵文字リアクション | メッセージへの絵文字リアクション |
| F-12 メンション | `@user` でユーザーを呼び出し |
| F-13 メッセージ検索 | ワークスペース内のメッセージ全文検索 |
| F-14 通知 | 未読メッセージ数・メンション通知 |
| F-15 招待機能 | オーナーがワークスペース / チャンネルに招待 |
| F-16 管理者操作 | オーナーによるユーザーキック・チャンネル削除 |

---

## 技術スタック（暫定）

確定版は設計フェーズで `docs/tech-stack.md` を作成する。

| レイヤー | 主要技術 |
| --- | --- |
| フロントエンド | Next.js 14.x + TypeScript + Tailwind CSS |
| バックエンド | Spring Boot 3.x + Java 21 |
| リアルタイム通信 | WebSocket（STOMP over SockJS） |
| データベース | PostgreSQL 17 |
| キャッシュ / Pub-Sub | Redis 7 系 |
| 認証 | Spring Security + JWT（JJWT） |
| ファイルストレージ | AWS S3 |
| インフラ | AWS / Render のいずれか（後続講義に合わせて決定） |

---

## 開発フェーズ（このリポジトリの進め方）

本プロジェクトは以下の順で進める。Claude Code 自動レビューは **実装が一通り揃った後半（④）** で導入する。要件定義段階や設計段階で先に走らせても、まだコード差分が少なくレビューとして意味のある出力が得られないため。

| フェーズ | 内容 | 状態 |
| --- | --- | --- |
| ① 要件定義 | `why-slack.md` / `requirements.md` / `functional-requirements.md` を作成 | ✅ 完了 |
| ② 設計 | 画面・DB・技術スタック・インフラ・リアルタイム / キャッシュ設計を確定 | ⏳ |
| ③ 実装 | バックエンド機能 → フロントエンド → 結合 | ⏳ |
| ④ 自動レビュー導入 | 実装が一通り揃った後半で `.github/workflows/claude-code-review.yml` を導入し、複数機能が乗った PR で動作確認（トークン消費を考慮し、最終課題提出時の運用方針は別途判断） | ⏳ |
| ⑤ デプロイ・運用 | AWS / Render どちらかへの自動デプロイ、Ansible、監視 | ⏳ |

---

## ローカル開発セットアップ

設計・実装フェーズが進んだ段階で記載する（**TBD**）。

予定ポート割当:

| サーバー | ポート |
| --- | --- |
| フロントエンド（Next.js） | 3000 |
| バックエンド（Spring Boot） | 8080 |
| データベース（PostgreSQL） | 5432 |
| キャッシュ / Pub-Sub（Redis） | 6379 |
