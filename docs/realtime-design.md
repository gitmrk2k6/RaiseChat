# リアルタイム通信設計書 — RaiseChat（WebSocket / STOMP / Redis Pub-Sub）

関連: [要件定義書](requirements.md) / [機能要件書](functional-requirements.md) / [API 設計書](api-design.md) / [データベース設計書](database-design.md)

---

## 0. はじめに

### 0.1 本書のゴール

RaiseChat の **リアルタイム通信プロトコル** を定義する。
具体的には以下を確定させる:

- どの URL でクライアントとサーバーが常時接続するか
- 接続時にどう認証するか
- 「新規メッセージ」「編集」「リアクション」などのイベントをどんな JSON 形で送受信するか
- バックエンドを複数台に増やしたとき、どうやって同じイベントを全員に届けるか
- 切断・再接続をどう扱うか

### 0.2 本書のスコープ

| 範囲 | 状態 |
| --- | --- |
| WebSocket / STOMP の接続プロトコル | ✅ 本書で定義 |
| STOMP destination 設計 | ✅ 本書で定義 |
| WebSocket で流す JSON メッセージスキーマ | ✅ 本書で定義 |
| Redis Pub-Sub の **配信用チャネル** 設計 | ✅ 本書で定義（複数インスタンス対応のため） |
| 認可ルール（誰がどの destination を購読できるか）| ✅ 本書で定義 |

### 0.3 本書のスコープ外

| トピック | 担当ドキュメント |
| --- | --- |
| Redis を **キャッシュとして使う** 戦略（TTL / key 設計 / 無効化） | [docs/cache-strategy.md](cache-strategy.md) |
| REST API の URL・スキーマ | [docs/api-design.md](api-design.md) |
| 画面遷移・WebSocket イベントを UI にどう反映するか | `docs/screen-design.md`（D-4、後続作成） |
| 実装コード（`WebSocketConfig` 等の Java クラス） | 実装フェーズ（本書は仕様のみ） |

### 0.4 本書を読む前提

REST 側の方針（[docs/api-design.md §1](api-design.md)）と矛盾しないこと。具体的には:

- JSON は **camelCase**
- 日時は **ISO 8601 UTC**（例: `2026-05-28T12:34:56Z`）
- 認証は **JWT Bearer**
- ID は数値（BIGINT を JSON number で）
- エラー形式は **RFC 7807 ProblemDetail** と整合（WebSocket は STOMP ERROR フレームを使うが、ペイロードは ProblemDetail 互換にする）

---

## 1. 用語定義

専門用語を使う前にまとめて定義する。本書中で初出した用語が分からなくなったらここに戻ること。

| 用語 | ひと言で | 補足 |
| --- | --- | --- |
| **WebSocket** | ブラウザとサーバーが **常時つなぎっぱなしで双方向に話せる** 通信路 | HTTP の「お願い 1 回 → 返事 1 回」モデルの弱点を補う。サーバーから話しかけられる |
| **STOMP** | WebSocket の上で動く **メッセージのお作法**（Simple Text Oriented Messaging Protocol） | 「どこ宛 (destination) に / どんな種類 (frame) のメッセージか」を表す共通フォーマット。Pub-Sub の語彙を提供 |
| **SockJS** | WebSocket が使えない環境のための **フォールバック**ライブラリ | 旧ブラウザや一部プロキシ環境で HTTP ロングポーリング等にフォールバックする |
| **frame**（フレーム） | STOMP の **1 通のメッセージの単位** | `CONNECT` / `SUBSCRIBE` / `SEND` / `MESSAGE` / `ERROR` などの種別がある |
| **destination** | STOMP における **宛先文字列**（`/topic/channels/5` 等） | URL のような階層構造で、購読 (`SUBSCRIBE`) や送信 (`SEND`) のターゲットを表す |
| **session**（セッション） | 1 つの WebSocket 接続の単位 | 1 ブラウザタブ = 1 session が基本。複数タブを開けば session も増える |
| **subscribe**（購読） | 「この destination 宛のメッセージが来たら教えて」とサーバーに登録する操作 | 1 session が複数 destination を購読できる |
| **broker**（ブローカー） | 「どの session がどの destination を購読しているか」を覚えていて、配信を仲介する役 | Spring Boot 標準の **simple broker**（プロセス内）か、外部 broker（RabbitMQ 等）が使える |
| **Pub-Sub**（Publish / Subscribe） | 「publish された 1 通のメッセージを、購読者全員に配る」配信モデル | broker の基本動作。RaiseChat は Redis の Pub-Sub 機能でこれを行う |
| **Redis Pub-Sub** | Redis が提供する Pub-Sub 機能 | Redis の `PUBLISH` / `SUBSCRIBE` コマンドで使う。**メッセージの永続化はしない**（流すだけ） |
| **handshake**（ハンドシェイク） | WebSocket の **接続開始時に 1 回だけ行う HTTP リクエスト**（HTTP → WebSocket への昇格） | この HTTP リクエストに JWT を載せて認証する |
| **JWT**（JSON Web Token） | 「誰がログイン中か」をサーバーが署名付きで証明したトークン | RaiseChat は REST と同じトークンを WebSocket でも使う |

---

## 2. 全体アーキテクチャ

### 2.1 ひと言で

クライアントは Spring Boot に WebSocket で常時接続する。
バックエンドが複数台ある場合、各バックエンドが Redis Pub-Sub を経由して他のバックエンドに配信を依頼する。

### 2.2 構成図

```
┌──────────────┐                 ┌──────────────┐
│   Client A   │                 │   Client B   │
│ (Browser)    │                 │ (Browser)    │
└──────┬───────┘                 └──────┬───────┘
       │ STOMP over WebSocket           │ STOMP over WebSocket
       │ (/ws + JWT)                    │ (/ws + JWT)
       ▼                                ▼
┌─────────────────────┐         ┌─────────────────────┐
│  Spring Boot #1     │         │  Spring Boot #2     │
│  - WebSocketConfig  │         │  - WebSocketConfig  │
│  - simple broker    │         │  - simple broker    │
│  - JWT interceptor  │         │  - JWT interceptor  │
└──────────┬──────────┘         └──────────┬──────────┘
           │                                │
           │   PUBLISH / SUBSCRIBE          │
           └────────────┬───────────────────┘
                        ▼
                 ┌─────────────┐
                 │    Redis    │
                 │ (Pub-Sub)   │
                 └─────────────┘
                        │
                        │   永続化が必要なものは PostgreSQL に保存
                        ▼
                 ┌─────────────┐
                 │ PostgreSQL  │
                 └─────────────┘
```

ポイント:

- クライアント ↔ Spring Boot 1 対 1: **STOMP over WebSocket**
- Spring Boot ↔ Spring Boot 間: **Redis Pub-Sub**（複数インスタンス時のメッセージ橋渡し）
- メッセージ本体の永続化は **PostgreSQL**（WebSocket はあくまで配信路、保存ではない）

### 2.3 broker の選択方針

Spring の WebSocket では broker を 2 種類から選べる:

| broker | 特徴 |
| --- | --- |
| **simple broker**（プロセス内）+ 自前 Redis Pub-Sub 橋渡し | 軽量。MVP 向き。橋渡し用 `RedisMessageListenerContainer` を自分で書く必要がある |
| **StompBrokerRelay**（RabbitMQ / ActiveMQ 等の外部 broker に丸投げ）| broker が代わりに分散を吸収。インフラに broker を立てる必要がある |

**RaiseChat MVP は前者（simple broker + Redis Pub-Sub 自前橋渡し）を採用する。**
理由: 既に Redis をキャッシュ用途で立てる予定があり、追加コンポーネントを増やしたくない。学習負荷も低い。

---

## 3. 接続フロー

### 3.1 ハンドシェイク URL

```
ws://<host>/ws        （開発環境）
wss://<host>/ws       （本番環境、TLS 上）
```

- パス: `/ws`（固定）
- SockJS フォールバックを有効化（旧ブラウザ・一部プロキシ対応）

### 3.2 認証（STOMP CONNECT フレームに JWT を載せる）

WebSocket のハンドシェイクは HTTP リクエストだが、ブラウザ標準の `WebSocket` API では **任意の HTTP ヘッダを付けられない**（`Authorization` ヘッダも不可）。
代わりに **STOMP の CONNECT フレームのヘッダ** に JWT を載せる方式を採る。

```
CONNECT
accept-version:1.2
host:raisechat.example.com
Authorization:Bearer eyJhbGciOiJIUzI1NiIs...

^@
```

サーバー側のフロー:

1. WebSocket セッション確立（ハンドシェイク完了）
2. クライアントから `CONNECT` フレームが届く
3. Spring の `ChannelInterceptor` が `Authorization` ヘッダから JWT を取り出す
4. 既存の `JwtTokenProvider`（REST と共通）で検証
5. 成功: STOMP `CONNECTED` フレームを返し、`Principal` を session に紐付ける
6. 失敗: STOMP `ERROR` フレームを返して接続を閉じる

### 3.3 接続シーケンス図

```
Client                         Server
  │                               │
  │── HTTP GET /ws (Upgrade) ────▶│  ハンドシェイク
  │◀──── 101 Switching ───────────│
  │                               │
  │── STOMP CONNECT (JWT 付) ────▶│  JWT 検証
  │◀──── STOMP CONNECTED ─────────│  session に Principal を紐付け
  │                               │
  │── SUBSCRIBE /topic/... ──────▶│  認可チェック → 購読登録
  │◀──── MESSAGE（配信時）─────────│
  │                               │
  │── SEND /app/... ─────────────▶│  メッセージ処理 + ブロードキャスト
  │                               │
  │── DISCONNECT ────────────────▶│  session クリーンアップ
```

### 3.4 認証失敗時の挙動

| 状況 | サーバーの応答 |
| --- | --- |
| `Authorization` ヘッダなし | STOMP `ERROR` フレーム（`code: AUTH_REQUIRED`）→ 切断 |
| JWT 不正・改ざん | STOMP `ERROR`（`code: INVALID_TOKEN`）→ 切断 |
| JWT 期限切れ | STOMP `ERROR`（`code: TOKEN_EXPIRED`）→ 切断 |
| 認可不足（後述）| STOMP `ERROR`（`code: FORBIDDEN`）。接続は維持。該当 SUBSCRIBE のみ失敗 |

`ERROR` フレームのペイロードは [§6.5](#65-エラーペイロード) で定義。

---

## 4. STOMP destination 設計

### 4.1 destination の階層

STOMP の destination は URL に似た階層を持つ。RaiseChat では 3 つのプレフィックスを使い分ける。

| プレフィックス | 方向 | 用途 |
| --- | --- | --- |
| `/app/**` | **クライアント → サーバー** | クライアントが `SEND` する宛先。サーバーの `@MessageMapping` ハンドラが受ける |
| `/topic/**` | **サーバー → クライアント（複数購読者）** | チャンネル / DM / スレッドのブロードキャスト用 |
| `/user/queue/**` | **サーバー → クライアント（特定ユーザー個人宛）** | 通知（メンション・未読更新など）。同じ destination でも宛先ユーザーごとに分離される |

### 4.2 destination 一覧

#### 4.2.1 クライアント → サーバー（`/app/**`）

| destination | 用途 | 対応機能 |
| --- | --- | --- |
| `/app/channels/{channelId}/messages` | チャンネル新規メッセージ送信 | F-05 |
| `/app/dm/{roomId}/messages` | DM 新規メッセージ送信 | F-06 |
| `/app/channels/{channelId}/typing` | タイピング状態通知 | F-05 関連（補助） |
| `/app/dm/{roomId}/typing` | DM タイピング状態通知 | F-06 関連（補助） |
| `/app/channels/{channelId}/read` | 既読位置の通知 | F-14 |

> **メッセージ編集・削除・リアクション追加 / 解除は WebSocket 送信しない**。REST で受け、サーバー側でブロードキャストする（[docs/api-design.md §6](api-design.md) と一致）。

#### 4.2.2 サーバー → クライアント・ブロードキャスト（`/topic/**`）

| destination | 配信されるイベント | 対応機能 |
| --- | --- | --- |
| `/topic/channels/{channelId}` | `message.created` / `message.updated` / `message.deleted` / `reaction.added` / `reaction.removed` / `typing.started` / `typing.stopped` | F-05, F-07, F-11 |
| `/topic/dm/{roomId}` | 同上（DM 文脈） | F-06, F-07, F-11 |
| `/topic/threads/{parentMessageId}` | `message.created` / `message.updated` / `message.deleted` / `reaction.*`（スレッド内）| F-08, F-07, F-11 |
| `/topic/workspaces/{workspaceId}/presence` | `presence.changed`（オンライン状態）| F-14 補助 |

#### 4.2.3 個人宛（`/user/queue/**`）

| destination | 用途 | 対応機能 |
| --- | --- | --- |
| `/user/queue/notifications` | メンション通知、未読カウント更新、招待 | F-14, F-15 |
| `/user/queue/errors` | サーバー側で個別エラーを返したい時 | 横断 |

> `/user/**` プレフィックスは Spring の規約。Principal 名で自動的に宛先が分離される。クライアントは `/user/queue/notifications` を購読すれば自分宛だけ届く。

---

## 5. メッセージスキーマ（共通エンベロープ）

### 5.1 設計判断

WebSocket で流す JSON はすべて **同じ封筒（envelope）** に入れる。
こうしておくと、クライアントは `type` を見るだけで処理を振り分けられる。REST と違って 1 つの destination に複数の種類のイベントが流れるため、`type` フィールドが必須。

### 5.2 共通エンベロープ

```json
{
  "type": "message.created",
  "serverTime": "2026-05-28T12:34:56Z",
  "payload": { /* type ごとに異なる */ }
}
```

| フィールド | 型 | 説明 |
| --- | --- | --- |
| `type` | string | イベント種別。ドット区切り（`<resource>.<verb>` 形式） |
| `serverTime` | string (ISO 8601 UTC) | サーバーが配信した時刻。クライアント時計との突き合わせに使う |
| `payload` | object | `type` ごとに異なるデータ。形は §6 で定義 |

### 5.3 イベント種別一覧

| `type` | 配信される destination | 発火タイミング |
| --- | --- | --- |
| `message.created` | `/topic/channels/{id}` / `/topic/dm/{id}` / `/topic/threads/{id}` | 新規メッセージ受信時 |
| `message.updated` | 同上 | REST `PUT /api/messages/{id}` 成功時 |
| `message.deleted` | 同上 | REST `DELETE /api/messages/{id}` 成功時 |
| `reaction.added` | 同上 | REST `POST /api/messages/{id}/reactions` 成功時 |
| `reaction.removed` | 同上 | REST `DELETE /api/messages/{id}/reactions/{emoji}` 成功時 |
| `typing.started` | `/topic/channels/{id}` / `/topic/dm/{id}` | クライアントがタイピング開始通知を送った時 |
| `typing.stopped` | 同上 | クライアントがタイピング停止通知 / 一定時間無操作 |
| `presence.changed` | `/topic/workspaces/{id}/presence` | ユーザーが接続 / 切断 / アイドル状態変化時 |
| `read.updated` | `/user/queue/notifications` | 自分の既読位置が他のタブで更新された時 |
| `notification.mention` | `/user/queue/notifications` | メンションされた時 |
| `notification.invite` | `/user/queue/notifications` | ワークスペース / チャンネルに招待された時 |

---

## 6. ペイロード詳細

### 6.1 `message.created`

```json
{
  "type": "message.created",
  "serverTime": "2026-05-28T12:34:56Z",
  "payload": {
    "id": 1001,
    "workspaceId": 1,
    "channelId": 5,
    "dmRoomId": null,
    "parentMessageId": null,
    "authorUserId": 12,
    "author": {
      "id": 12,
      "userId": "mrk2k6",
      "displayName": "Mrk",
      "avatarUrl": null,
      "statusMessage": ""
    },
    "body": "おはようございます",
    "editedAt": null,
    "createdAt": "2026-05-28T12:34:56Z"
  }
}
```

- `payload` は REST の `Message` スキーマ（[docs/api-design.md §4.4](api-design.md)）と完全一致させる
- スレッド返信なら `parentMessageId`（スレッドの root ID）が入り、イベントは **`/topic/threads/{rootId}` のみ** に配信する（チャンネル / DM トピックには流さない。チャンネル / DM 履歴は返信を除外しているため、ミラー配信＝"also send to channel" は post-MVP）。実装は #63
- DM なら `channelId: null` / `dmRoomId` が入り、`/topic/dm/{roomId}` に配信

### 6.2 クライアント → サーバー（`message.send`）

クライアントが `/app/channels/{channelId}/messages` に送る JSON。サーバー側で組み立てた `message.created` を全員に配信する。

```json
{
  "clientMessageId": "c-7f3a2b",
  "body": "おはようございます",
  "parentMessageId": null
}
```

| フィールド | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| `clientMessageId` | string | 必須 | クライアントが生成する一時 ID。送信エコー時の重複検出に使う |
| `body` | string | 必須 | メッセージ本文（1〜4000 文字、REST と同じ制約） |
| `parentMessageId` | number \| null | 任意 | スレッド返信なら親メッセージ ID |

#### `clientMessageId` の役割

WebSocket は ack（受信確認）の概念がないため、クライアントは「自分が送ったメッセージが配信時に返ってきたか」を識別する必要がある。
サーバーは `message.created` を配信する際に **送信者本人にも同じ payload を流す**（楽観的 UI の確定用）。クライアントは `clientMessageId` を元のローカルメッセージと突き合わせて確定状態に切り替える。

> ※ サーバー → クライアントのレスポンス側に `clientMessageId` を含めるかは実装フェーズで詰める。MVP では含める案を推奨（[§11](#11-段階的実装の-todo) 参照）。

### 6.3 `message.updated` / `message.deleted`

```json
{
  "type": "message.updated",
  "serverTime": "2026-05-28T12:40:00Z",
  "payload": { /* 更新後の Message 全体 */ }
}
```

```json
{
  "type": "message.deleted",
  "serverTime": "2026-05-28T12:41:00Z",
  "payload": {
    "id": 1001,
    "channelId": 5,
    "dmRoomId": null,
    "parentMessageId": null
  }
}
```

削除は本文を返さない。クライアントは ID で該当メッセージを UI から取り除く。

### 6.4 `reaction.added` / `reaction.removed`

```json
{
  "type": "reaction.added",
  "serverTime": "2026-05-28T12:50:00Z",
  "payload": {
    "messageId": 1001,
    "emoji": "thumbsup",
    "userId": 15,
    "count": 3
  }
}
```

| フィールド | 説明 |
| --- | --- |
| `emoji` | 絵文字の短縮名（Slack 互換、`:thumbsup:` の中身） |
| `userId` | 操作したユーザー |
| `count` | 配信時点での合計カウント（UI 反映を 1 イベントで済ませるため） |

### 6.5 エラーペイロード（STOMP `ERROR` フレーム / `/user/queue/errors`）

STOMP の `ERROR` フレーム本体は **RFC 7807 ProblemDetail** 互換にする。REST と同じ形でクライアントは扱える。

```json
{
  "type": "https://raisechat.example.com/problems/forbidden",
  "title": "Forbidden",
  "status": 403,
  "detail": "このチャンネルへの購読権限がありません",
  "code": "FORBIDDEN",
  "destination": "/topic/channels/5"
}
```

- `code` は拡張フィールド。`AUTH_REQUIRED` / `INVALID_TOKEN` / `TOKEN_EXPIRED` / `FORBIDDEN` / `NOT_FOUND` / `RATE_LIMITED` / `INTERNAL` の 7 種を定義
- `destination` は対象 destination（接続レベルのエラーでは省略）

### 6.6 `typing.started` / `typing.stopped`

```json
{
  "type": "typing.started",
  "serverTime": "2026-05-28T12:34:50Z",
  "payload": {
    "channelId": 5,
    "dmRoomId": null,
    "userId": 12
  }
}
```

- DB には保存しない（流して消える情報）
- 一定時間（既定 5 秒）無更新で自動的に `typing.stopped` をサーバーから発火

### 6.7 `presence.changed`

```json
{
  "type": "presence.changed",
  "serverTime": "2026-05-28T12:00:00Z",
  "payload": {
    "userId": 12,
    "state": "online"
  }
}
```

`state` は `"online" | "away" | "offline"`。実装はワークスペース単位の `/topic/workspaces/{id}/presence` に配信。

---

## 7. Redis Pub-Sub 配信トポロジ

### 7.1 なぜ必要か

バックエンドを **複数インスタンスに冗長化** すると、次の問題が起きる:

```
[Client A]──→ [Spring Boot #1] ←── /topic/channels/5 購読
[Client B]──→ [Spring Boot #2] ←── /topic/channels/5 購読

Client A が #1 経由でメッセージ送信
  → #1 は自プロセス内の broker しか知らない
  → Client B には届かない ❌
```

これを解決するため、**送信を受けたインスタンスは Redis に PUBLISH し、全インスタンスが SUBSCRIBE して自分が持つ session に配信する**。

```
Client A ─→ Spring Boot #1 ─→ Redis PUBLISH ─┐
                                              ├─→ Spring Boot #1 → Client A の session
                                              ├─→ Spring Boot #2 → Client B の session
                                              └─→ Spring Boot #3 → ...（あれば）
```

### 7.2 Redis チャネル命名（粒度の判断）

Redis Pub-Sub の **チャネル** = どの単位で publish / subscribe するかの単位。粒度を細かくするか粗くするかにトレードオフがある。

| 案 | チャネル例 | 利点 | 欠点 |
| --- | --- | --- | --- |
| A. 全部 1 本 | `ws:all` | 実装最簡単 | 関係ないインスタンスにも届くので全インスタンスが全イベントを受信 |
| B. ワークスペース単位 | `ws:workspace:{id}` | 粒度バランス。ワークスペース内ブロードキャストと自然に対応 | DM のような **ワークスペースをまたがない範囲** にも全ワークスペース宛が流れる |
| C. destination 単位 | `ws:topic:channels:5` | 完全に必要なインスタンスにだけ届く | チャネル数が膨大。Redis 側のメモリ負荷・SUBSCRIBE 数増 |

**RaiseChat は案 B（ワークスペース単位）を採用する。**
理由:

- RaiseChat の実運用では同一ワークスペースのユーザーは同じバックエンドに偏る傾向がある（ロードバランサのスティッキーセッション併用時）
- それでも 100% 同居しないので Redis 橋渡しは必要
- チャネル数が「ワークスペース数」で抑えられ運用しやすい
- DM もワークスペース内 1 対 1 のため、ワークスペース単位で配信して問題ない

### 7.3 publish するペイロード

Redis に流すペイロードは「どこに配信すべきか + 配信内容」の 2 つを持たせる:

```json
{
  "destination": "/topic/channels/5",
  "envelope": {
    "type": "message.created",
    "serverTime": "2026-05-28T12:34:56Z",
    "payload": { /* ... */ }
  }
}
```

- 各インスタンスは Redis SUBSCRIBE を受けて `destination` 宛の購読 session を自プロセスから探し、`envelope` を MESSAGE フレームで配信する
- インスタンス自身が送信元の場合も同じ経路を通すか、ローカル経路を別途持つかは実装フェーズで判断（重複配信を避けるなら publish 時に「自インスタンスは除外」フラグを付ける案がある）

### 7.4 `/user/queue/**`（個人宛）の扱い

`/user/**` は Spring が内部的に session 単位の宛先に解決する。複数インスタンス時、宛先ユーザーがどのインスタンスに繋がっているかは事前に分からないため:

- Redis チャネルは **ワークスペース単位** に同じく相乗りさせる（`ws:workspace:{id}`）
- envelope に `targetUserId` を追加し、各インスタンスは「自分がその userId の session を持っていれば配信、なければ無視」する

```json
{
  "destination": "/user/queue/notifications",
  "targetUserId": 15,
  "envelope": { /* ... */ }
}
```

### 7.5 D-3 への引き継ぎ

本書では **「ワークスペース単位でチャネルを 1 本切る」** ところまで決める。
以下は D-3（[docs/cache-strategy.md](cache-strategy.md)）で扱う:

- Redis の **キャッシュ用途**（メッセージ直近 N 件 / 未読カウント / チャンネル一覧）の key 設計と TTL
- キャッシュ無効化のトリガ（メッセージ送信時にどの key を消すか）
- Pub-Sub と通常 KV ストアの **同一 Redis を共有してよいか、別 DB index を切るか** の方針

---

## 8. 認可モデル

### 8.1 destination ごとの購読権限

`SUBSCRIBE` フレームを受けたとき、サーバーは以下のルールで認可を判定する。

| destination | 購読許可条件 |
| --- | --- |
| `/topic/channels/{id}` | チャンネルメンバーである / または「パブリックチャンネル」かつ同一ワークスペースのメンバー |
| `/topic/dm/{roomId}` | DM ルームの参加者である |
| `/topic/threads/{parentMessageId}` | 親メッセージのチャンネル / DM の閲覧権限を継承 |
| `/topic/workspaces/{id}/presence` | 当該ワークスペースのメンバー |
| `/user/queue/notifications` | 自分（Principal の userId）のみ。他人のキューは購読不可 |
| `/user/queue/errors` | 自分のみ |

### 8.2 SEND の認可

`/app/**` への送信時もチェック:

| destination | 送信許可条件 |
| --- | --- |
| `/app/channels/{id}/messages` | チャンネルメンバーである（パブリックでも書き込みはメンバー限定） |
| `/app/dm/{roomId}/messages` | DM 参加者である |
| `/app/channels/{id}/typing` | チャンネルメンバー |
| `/app/dm/{roomId}/typing` | DM 参加者 |
| `/app/channels/{id}/read` | チャンネルメンバー（自分の既読のみ更新可） |

### 8.3 判定の実装方針（D-3 と連携）

毎回 DB に問い合わせると性能が出ないので:

- 接続時に **「自分が所属するワークスペース / チャンネル / DM」** のリストを取り、session スコープにキャッシュする想定
- 変更（参加・退出・キック）時はキャッシュ無効化が必要
- キャッシュ key の具体は **D-3 で決める**。本書では「session に紐付けてキャッシュする方針を採る」とだけ宣言する

### 8.4 認可失敗時の応答

| 状況 | サーバー応答 |
| --- | --- |
| SUBSCRIBE 認可失敗 | `/user/queue/errors` に ProblemDetail（`code: FORBIDDEN`, `destination` 入り）を配信。SUBSCRIBE は不成立 |
| SEND 認可失敗 | 同上。メッセージは破棄、保存しない |
| 接続レベル認可失敗（ワークスペース未所属でその topic に subscribe しようとした等）| 接続は維持。当該 SUBSCRIBE のみ失敗 |

---

## 9. 再接続・エラーハンドリング

### 9.1 クライアント側の再接続戦略

| 状況 | 戦略 |
| --- | --- |
| ネットワーク切断 | **指数バックオフ**で再接続。初回 1 秒 → 2 → 4 → 8 → 上限 30 秒 |
| トークン期限切れ（`code: TOKEN_EXPIRED`）| REST `/api/auth/refresh` で新トークン取得 → 再接続 |
| サーバー側で接続を切られた（`code: FORBIDDEN` 等）| 自動再接続しない。UI に通知し、ユーザー操作で再試行 |
| ブラウザがフォアグラウンドに復帰 | 即座に接続状態を確認、未接続なら即再接続 |

### 9.2 切断中に流れたメッセージの補完

WebSocket には永続化機能がないため、切断中のメッセージは取りこぼす。
補完は **REST 履歴 API** で行う:

```
GET /api/channels/{id}/messages?cursor=<最後に受信した ID>
```

クライアントは「最後に受信したメッセージ ID」を保持し、再接続時に REST で差分取得する。

### 9.3 サーバー側のエラーフレーム

[§6.5](#65-エラーペイロード) で定義した ProblemDetail 互換形を STOMP `ERROR` フレームの body に入れて返す。

- 接続レベルエラー: `ERROR` フレーム → 切断
- 操作レベルエラー（SUBSCRIBE / SEND 個別）: `/user/queue/errors` に MESSAGE を流し、接続は維持

### 9.4 重複配信の検出

Redis 経由で配信する都合上、稀に同じイベントが 2 回届くケースを想定する。クライアントは:

- `payload.id`（DB のメッセージ ID）で重複検出
- `clientMessageId`（自分が送ったメッセージのエコー）で楽観的 UI の確定

を行い、UI 側で冪等に処理する。

### 9.5 タイミング起因の取りこぼし（推奨フロー）

ベストプラクティスとして、接続シーケンスは:

1. 接続 → `CONNECTED`
2. REST で履歴を取得（最新の ID を覚える）
3. SUBSCRIBE 開始
4. 配信で受け取った ID が REST 取得済みより大きいもののみ UI 反映

を採る。これにより「REST 取得と SUBSCRIBE 開始のすき間」を埋める。

---

## 10. 非機能要件への対応

| 要件（[要件定義書 §4](requirements.md)）| 本設計での対応 |
| --- | --- |
| 配信遅延 **1 秒以内** | Redis Pub-Sub の publish レイテンシは通常 1 ms 未満。STOMP の broker → session 配信もミリ秒オーダー。十分達成可能 |
| 複数インスタンス冗長化 | Redis Pub-Sub で全インスタンスに橋渡し（[§7](#7-redis-pub-sub-配信トポロジ)）|
| 通常操作 2 秒以内 | 認可キャッシュを session スコープに持たせて DB 問い合わせを削減 |
| プライベートチャンネル / DM の隔離 | destination 単位の認可チェック（[§8](#8-認可モデル)）|
| 構造化ログ | `sessionId` / `userId` / `destination` / `messageId` を全イベントログに含める想定（実装フェーズで定義） |

### 10.1 想定スケール（MVP）

- 同時接続: 数十 〜 数百セッション
- メッセージレート: 10 msg/sec 程度
- バックエンドインスタンス数: 1 〜 3

この規模では Redis Pub-Sub + simple broker で十分。
これを大きく超える規模になったら **StompBrokerRelay + RabbitMQ** への切り替えを検討する（後続フェーズ）。

---

## 11. 段階的実装の TODO

実装フェーズに入った時のチェックリスト。本書を満たす Spring Boot 側コンポーネントを以下の順で実装する想定。

### 11.1 依存追加（`backend/build.gradle`）

```gradle
implementation 'org.springframework.boot:spring-boot-starter-websocket'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

### 11.2 主要コンポーネント

| クラス | 責務 |
| --- | --- |
| `WebSocketConfig`（`@EnableWebSocketMessageBroker`） | `/ws` 登録 / SockJS / simple broker 設定 / `/app` `/topic` `/user` プレフィックス定義 |
| `StompAuthChannelInterceptor`（`ChannelInterceptor`） | CONNECT フレームから JWT 取得 → 検証 → Principal 設定 |
| `SubscriptionAuthorizationInterceptor` | SUBSCRIBE / SEND 時に destination 認可を判定 |
| `ChatMessageController`（`@MessageMapping`） | `/app/channels/{id}/messages` 等のハンドラ。DB 保存 → broadcaster へ |
| `MessageBroadcaster` | broker.send + Redis publish の窓口 |
| `RedisFanInListener`（`RedisMessageListener`）| Redis SUBSCRIBE → 自プロセスの broker.send へ橋渡し |
| `PresenceTracker` | session 接続 / 切断イベントを購読し `presence.changed` を発火 |

### 11.3 実装順（提案）

1. `WebSocketConfig` + 最小の echo ハンドラで疎通確認
2. `StompAuthChannelInterceptor` で JWT 認証
3. `/app/channels/{id}/messages` → `/topic/channels/{id}` の単一インスタンス配信
4. 認可チェックを追加
5. `MessageBroadcaster` + Redis 橋渡しで複数インスタンス対応
6. 編集 / 削除 / リアクション の REST → WebSocket 配信
7. `/user/queue/notifications` でメンション通知
8. typing / presence
9. クライアント側の再接続戦略（フロントエンド実装フェーズ）

---

## 12. 未決事項 / 今後検討

- **クライアントエコーに `clientMessageId` を含める方式**: 含める前提で書いたが、Spring の `@SendTo` で簡単に返せるか実装時に確認
- **重複配信抑止**: 送信元インスタンスで自身宛配信をスキップする実装パターンの選定
- **typing デバウンス**: クライアント側のデバウンス間隔（既定 1.5 秒で送信、5 秒無更新で自動停止案）の妥当性
- **オフライン presence の判定**: 「最後の session が切れた時」vs「猶予 30 秒」のどちらにするか
- **メッセージ順序保証**: 同一チャンネル内での順序は serverTime + id で十分か、Lamport クロック等を導入すべきか（MVP では不要と判断）
- **レート制限**: 1 ユーザー 1 秒あたり何メッセージまで許可するか。実装時に具体値を決める

---

## 13. 変更履歴

| 日付 | 変更内容 | PR |
| --- | --- | --- |
| 2026-05-28 | 初版作成（WebSocket / STOMP / Redis Pub-Sub の全体設計）| #28 |
