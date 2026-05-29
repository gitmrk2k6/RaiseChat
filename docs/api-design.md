# API 設計書 — RaiseChat

関連: [要件定義書](requirements.md) / [機能要件書](functional-requirements.md) / [データベース設計書](database-design.md) / [「Slack 風」の捉え方](why-slack.md)

---

## 0. はじめに

本書は RaiseChat の **REST API エンドポイント仕様** を定義する。
F-01〜F-08（MVP コア機能）の REST API について、URL・HTTP メソッド・リクエスト / レスポンス JSON スキーマ・認証要否・エラーパターンを確定させる。
WebSocket / STOMP のメッセージプロトコルは本書のスコープ外（[5.5](#55-チャンネルメッセージ-f-05-f-07) と [6](#6-websocket-との境界) で REST との分担のみ示し、詳細は `docs/realtime-design.md` で別途定義する）。

### 0.1 本書のスコープ

| 範囲 | 状態 |
| --- | --- |
| F-01 ユーザー認証 | ✅ 既存実装と整合（`/api/auth/signup`, `/login`, `/refresh`, `/me`） |
| F-02 プロフィール管理 | ✅ 本書で定義 |
| F-03 ワークスペース管理 | ✅ 本書で定義 |
| F-04 チャンネル管理 | ✅ 本書で定義 |
| F-05 チャンネルメッセージ | ✅ REST 部分（履歴取得・編集・削除）を定義。送信は WebSocket（別書） |
| F-06 ダイレクトメッセージ | ✅ REST 部分を定義 |
| F-07 メッセージ編集・削除 | ✅ 本書で定義（F-05 と一体） |
| F-08 スレッド | ✅ 本書で定義 |

### 0.2 本書のスコープ外

| トピック | 担当ドキュメント |
| --- | --- |
| WebSocket / STOMP / Redis Pub-Sub | `docs/realtime-design.md`（後続作成） |
| F-09〜F-16 の API（マークダウン・添付・リアクション・メンション・検索・通知・招待・管理） | 別 PR で本書に追補予定 |
| Redis キャッシュキー設計 | [docs/cache-strategy.md](cache-strategy.md) |
| 画面遷移・ワイヤーフレーム | `docs/screen-design.md`（後続作成） |

---

## 1. 設計方針（横断ルール）

以後のエンドポイント設計の判断基準となる原則を宣言する。個別のエンドポイント定義はこの原則を前提として読むこと。

| # | 原則 | 補足 |
| --- | --- | --- |
| 1 | **REST 原則・リソース指向** | URL は名詞、HTTP メソッドで動詞を表現。`POST /api/channels/{id}/join` のようなアクション URL は join/leave のみ例外的に許容 |
| 2 | **URL プレフィックスは `/api`** | バージョニング（`/api/v1`）は MVP 時点では導入しない。破壊的変更が発生したら導入を検討 |
| 3 | **JSON 命名は camelCase** | DB は snake_case、API は camelCase。Spring Boot のデフォルト（Jackson）に従う。既存 DTO（`SignupRequest` 等）と整合 |
| 4 | **認証は JWT Bearer** | `Authorization: Bearer <accessToken>` ヘッダ必須（公開エンドポイントを除く）。アクセストークン短期 + リフレッシュトークン長期 |
| 5 | **日時は ISO 8601 UTC** | `2026-05-28T12:34:56Z` 形式。表示時にクライアントで JST 変換 |
| 6 | **ID は数値（BIGINT）を JSON number で返す** | `id: 123`。JavaScript の安全整数上限（2^53-1）は当面問題にならない（MVP 規模）。将来超過リスクが見えたら string 化を検討 |
| 7 | **ページングは cursor ベース** | メッセージなど時系列リソースは offset/limit ではなく `?cursor=<opaqueId>&limit=50`。Slack 同様、無限スクロールと整合 |
| 8 | **エラーレスポンスは RFC 7807 ProblemDetail** | Spring Boot 3 標準の `ProblemDetail` 形式に準拠。`type` / `title` / `status` / `detail` / `instance` + 拡張フィールド `errors`（バリデーション詳細） |
| 9 | **論理削除は API 上は完全削除と区別しない** | DB は `deleted_at` で論理削除するが、API レスポンスでは「存在しない（404）」として扱う |

### 1.1 認証ヘッダの扱い

すべての保護エンドポイントは以下を要求する:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

- 不正・期限切れトークン: `401 Unauthorized`
- 認可不足（他人のメッセージを編集など）: `403 Forbidden`

### 1.2 ページング仕様

カーソルベース。クエリパラメータ:

| パラメータ | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| `cursor` | string | 任意 | 直前のレスポンスで返された `nextCursor`。省略時は最新から取得 |
| `limit` | int | 任意 | 取得件数。既定 50、最大 100 |

レスポンスの共通形:

```json
{
  "items": [ /* ... */ ],
  "nextCursor": "eyJpZCI6MTIzfQ==",
  "hasMore": true
}
```

- `nextCursor` は不透明文字列（base64 等）。クライアントは内容を解釈せずそのまま渡す
- 次ページがない場合 `nextCursor: null`, `hasMore: false`

---

## 2. 共通レスポンス

### 2.1 成功レスポンス

- **単一リソース**: リソースの JSON をそのまま返す（ラップしない）

```json
{
  "id": 42,
  "name": "general",
  "type": "PUBLIC",
  "createdAt": "2026-05-28T12:00:00Z"
}
```

- **リスト**: ページング共通形（[1.2](#12-ページング仕様) 参照）

### 2.2 エラーレスポンス（RFC 7807 ProblemDetail）

すべてのエラーは以下の共通形で返す。`Content-Type: application/problem+json`。

```json
{
  "type": "https://raisechat.example.com/problems/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "リクエストボディに不正な値があります",
  "instance": "/api/auth/signup",
  "errors": [
    { "field": "userId", "message": "userId は半角英数字・ハイフン・アンダースコア 3〜32 文字" },
    { "field": "password", "message": "8 文字以上必要です" }
  ]
}
```

- `type`: エラー種別の URI（仕様上は dereferenceable だが本プロジェクトでは識別子として使う）
- `title`: 人間可読の短い説明
- `status`: HTTP ステータスコード
- `detail`: より詳細なメッセージ（ユーザーに表示しても良い文言）
- `instance`: 当該リクエストの URI
- `errors`: 拡張フィールド。バリデーションエラー時のフィールド単位詳細

### 2.3 主要 HTTP ステータスコード

| ステータス | 用途 |
| --- | --- |
| `200 OK` | 取得・更新成功 |
| `201 Created` | リソース作成成功（`Location` ヘッダ任意） |
| `204 No Content` | 削除成功 / レスポンスボディなし |
| `400 Bad Request` | リクエスト形式エラー（JSON パース失敗など） |
| `401 Unauthorized` | 未認証・トークン不正 / 期限切れ |
| `403 Forbidden` | 認証済みだが操作権限なし |
| `404 Not Found` | リソース不在（論理削除済み含む） |
| `409 Conflict` | 一意制約違反（user_id 重複、DM ルーム重複作成など） |
| `410 Gone` | リソースは存在したが利用不可になった（招待リンクの期限切れ・無効化・使用上限到達など） |
| `422 Unprocessable Entity` | バリデーション違反（型は正しいが値が不正） |
| `500 Internal Server Error` | サーバー側未捕捉エラー |

- **400 と 422 の使い分け**: 形式が壊れている（JSON 不正・必須フィールド欠落で JSON パースに失敗するレベル）は 400、形式は正しいが値の制約違反は 422。Spring Boot の Bean Validation 失敗は 422 を基本とする。

---

## 3. エンドポイント一覧（俯瞰表）

| # | メソッド | パス | 認証 | 機能 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/auth/signup` | 不要 | F-01 ユーザー登録 |
| 2 | POST | `/api/auth/login` | 不要 | F-01 ログイン |
| 3 | POST | `/api/auth/refresh` | 不要（リフレッシュトークン必須） | F-01 アクセストークン再発行 |
| 4 | POST | `/api/auth/logout` | 必要 | F-01 ログアウト |
| 5 | GET | `/api/auth/me` | 必要 | F-01 自分のユーザー情報取得 |
| 6 | PUT | `/api/users/me` | 必要 | F-02 プロフィール更新 |
| 7 | POST | `/api/users/me/avatar` | 必要 | F-02 アバター画像アップロード |
| 8 | POST | `/api/workspaces` | 必要 | F-03 ワークスペース作成 |
| 9 | GET | `/api/workspaces` | 必要 | F-03 所属ワークスペース一覧 |
| 10 | GET | `/api/workspaces/{wsId}` | 必要 | F-03 ワークスペース詳細 |
| 11 | POST | `/api/workspaces/{wsId}/channels` | 必要 | F-04 チャンネル作成 |
| 12 | GET | `/api/workspaces/{wsId}/channels` | 必要 | F-04 チャンネル一覧 |
| 13 | GET | `/api/channels/{id}` | 必要 | F-04 チャンネル詳細 |
| 14 | POST | `/api/channels/{id}/join` | 必要 | F-04 チャンネル参加 |
| 15 | POST | `/api/channels/{id}/leave` | 必要 | F-04 チャンネル退出 |
| 16 | DELETE | `/api/channels/{id}` | 必要 | F-04 チャンネル削除 |
| 17 | GET | `/api/channels/{id}/messages` | 必要 | F-05 チャンネルメッセージ履歴取得 |
| 18 | PUT | `/api/messages/{id}` | 必要 | F-07 メッセージ編集 |
| 19 | DELETE | `/api/messages/{id}` | 必要 | F-07 メッセージ削除 |
| 20 | POST | `/api/workspaces/{wsId}/dm/rooms` | 必要 | F-06 DM ルーム作成 |
| 21 | GET | `/api/workspaces/{wsId}/dm/rooms` | 必要 | F-06 DM ルーム一覧 |
| 22 | GET | `/api/dm/rooms/{id}/messages` | 必要 | F-06 DM 履歴取得 |
| 23 | GET | `/api/messages/{parentId}/replies` | 必要 | F-08 スレッド返信一覧 |
| 24 | POST | `/api/messages/{parentId}/replies` | 必要 | F-08 スレッド返信投稿（送信は WebSocket 想定だが REST フォールバック用に定義） |
| 25 | POST | `/api/workspaces/{wsId}/invites` | 必要（OWNER） | F-15 ワークスペース招待リンク発行 |
| 26 | POST | `/api/invites/{token}/accept` | 必要 | F-15 招待受諾（呼び出しユーザーが参加） |
| 27 | DELETE | `/api/workspaces/{wsId}/invites/{inviteId}` | 必要（OWNER） | F-15 招待リンク無効化 |
| 28 | POST | `/api/channels/{id}/invites` | 必要（チャンネルメンバー） | F-15 チャンネル招待リンク発行 |
| 29 | POST | `/api/channel-invites/{token}/accept` | 必要（同一 WS メンバー） | F-15 チャンネル招待受諾（呼び出しユーザーが参加） |
| 30 | DELETE | `/api/channels/{id}/invites/{inviteId}` | 必要（チャンネルメンバー） | F-15 チャンネル招待リンク無効化 |

> **メッセージ送信について**: F-05 / F-06 の **新規メッセージ送信** は WebSocket（STOMP）経由が主であり、REST には用意しない。理由: クライアント・サーバー双方で配信経路を一本化することで、リアルタイム配信時のメッセージ重複・順序逆転を避けるため。スレッド返信（F-08）のみ MVP では REST も用意し、WebSocket 経路と並走させる検討余地を残す。詳細は [6. WebSocket との境界](#6-websocket-との境界) を参照。

---

## 4. 共通スキーマ

複数エンドポイントで使う JSON 型を先に定義する。詳細仕様セクションでは型名で参照する。

### 4.1 `User`

```json
{
  "id": 12,
  "userId": "mrk2k6",
  "displayName": "Mrk",
  "avatarUrl": "https://s3.example.com/avatars/12.png",
  "statusMessage": "学習中"
}
```

| フィールド | 型 | 説明 |
| --- | --- | --- |
| `id` | number | ユーザー PK（数値） |
| `userId` | string | ユーザーが入力する文字列 ID（DB の `users.user_id`） |
| `displayName` | string | 表示名 |
| `avatarUrl` | string \| null | アバター画像 URL（未設定は `null`） |
| `statusMessage` | string | ステータスメッセージ（最大 100 文字、未設定は `""`） |

### 4.2 `Workspace`

```json
{
  "id": 1,
  "name": "RaiseTech",
  "description": "AI エンジニアコース上級編",
  "ownerUserId": 12,
  "createdAt": "2026-05-28T12:00:00Z"
}
```

### 4.3 `Channel`

```json
{
  "id": 5,
  "workspaceId": 1,
  "name": "general",
  "description": "全体連絡",
  "type": "PUBLIC",
  "createdByUserId": 12,
  "createdAt": "2026-05-28T12:00:00Z"
}
```

`type` は `"PUBLIC" | "PRIVATE"`。

### 4.4 `Message`

```json
{
  "id": 1001,
  "workspaceId": 1,
  "channelId": 5,
  "dmRoomId": null,
  "parentMessageId": null,
  "authorUserId": 12,
  "author": { "id": 12, "userId": "mrk2k6", "displayName": "Mrk", "avatarUrl": null, "statusMessage": "" },
  "body": "おはようございます",
  "editedAt": null,
  "createdAt": "2026-05-28T12:00:00Z"
}
```

| フィールド | 型 | 説明 |
| --- | --- | --- |
| `channelId` | number \| null | チャンネルメッセージなら ID、DM なら `null` |
| `dmRoomId` | number \| null | DM メッセージなら ID、チャンネルメッセージなら `null` |
| `parentMessageId` | number \| null | スレッド返信なら親メッセージ ID、トップレベルなら `null` |
| `author` | User | 投稿者の `User` サブセット。N+1 回避のため埋め込み |
| `editedAt` | string \| null | 編集済みなら編集時刻、未編集なら `null` |

論理削除されたメッセージは API レスポンスに含めない（[1](#1-設計方針横断ルール) 原則 9）。

### 4.5 `DmRoom`

```json
{
  "id": 7,
  "workspaceId": 1,
  "members": [
    { "id": 12, "userId": "mrk2k6", "displayName": "Mrk", "avatarUrl": null, "statusMessage": "" },
    { "id": 15, "userId": "alice", "displayName": "Alice", "avatarUrl": null, "statusMessage": "" }
  ],
  "createdAt": "2026-05-28T12:00:00Z"
}
```

`members` は `User` の配列（1 対 1 DM なので常に 2 件）。

---

## 5. 詳細仕様

各エンドポイントは以下のテンプレートで記述する:

- **目的** / **認証** / **メソッド + パス** / **リクエスト** / **レスポンス** / **エラー**

### 5.1 認証（F-01）

#### 5.1.1 POST /api/auth/signup

- **目的**: 新規ユーザー登録 + アクセス / リフレッシュトークン発行
- **認証**: 不要
- **リクエスト**:

```json
{
  "userId": "mrk2k6",
  "displayName": "Mrk",
  "password": "secretPass1"
}
```

| フィールド | 制約 |
| --- | --- |
| `userId` | 必須、3〜32 文字、`^[A-Za-z0-9_-]+$`、ユニーク |
| `displayName` | 必須、1〜32 文字 |
| `password` | 必須、8〜72 文字、英字 + 数字を含む |

- **レスポンス** `201 Created`:

```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "expiresIn": 900
}
```

`expiresIn` はアクセストークンの有効秒数。

- **エラー**:
  - `409 Conflict`: `userId` 重複
  - `422 Unprocessable Entity`: バリデーション違反

#### 5.1.2 POST /api/auth/login

- **目的**: ユーザー ID + パスワードでログイン、トークン発行
- **認証**: 不要
- **リクエスト**:

```json
{ "userId": "mrk2k6", "password": "secretPass1" }
```

- **レスポンス** `200 OK`: signup と同形（`accessToken` / `refreshToken` / `expiresIn`）
- **エラー**:
  - `401 Unauthorized`: ユーザー ID またはパスワードが不正（ユーザー存在の有無で差をつけない）
  - `422 Unprocessable Entity`: バリデーション違反

#### 5.1.3 POST /api/auth/refresh

- **目的**: リフレッシュトークンを使って新しいアクセストークンを発行
- **認証**: アクセストークンは不要だがリフレッシュトークンが必須
- **リクエスト**:

```json
{ "refreshToken": "eyJhbGc..." }
```

- **レスポンス** `200 OK`: signup と同形
  - 新しい `refreshToken` も同時に発行（リフレッシュトークンのローテーション）
- **エラー**:
  - `401 Unauthorized`: トークン不正・期限切れ・失効済み

#### 5.1.4 POST /api/auth/logout

- **目的**: 現在のリフレッシュトークンを失効させる
- **認証**: 必要（アクセストークン）
- **リクエスト**:

```json
{ "refreshToken": "eyJhbGc..." }
```

- **レスポンス** `204 No Content`
- **エラー**:
  - `401 Unauthorized`: アクセストークン不正
  - `404 Not Found`: 指定リフレッシュトークンが存在しない（または他人のトークン）

> 既存実装には未含。本書で仕様を確定し、後続 Issue で実装する。

#### 5.1.5 GET /api/auth/me

- **目的**: 現在のログインユーザー情報を取得
- **認証**: 必要
- **リクエスト**: なし
- **レスポンス** `200 OK`: `User`
- **エラー**:
  - `401 Unauthorized`

### 5.2 プロフィール（F-02）

#### 5.2.1 PUT /api/users/me

- **目的**: 自分の表示名・ステータスメッセージを更新
- **認証**: 必要
- **リクエスト**:

```json
{
  "displayName": "Mrk2k6",
  "statusMessage": "実装中"
}
```

| フィールド | 制約 |
| --- | --- |
| `displayName` | 任意、指定時 1〜32 文字 |
| `statusMessage` | 任意、0〜100 文字 |

両方任意。指定されたフィールドのみ更新する（PATCH 的セマンティクス）。

- **レスポンス** `200 OK`: 更新後の `User`
- **エラー**:
  - `401 Unauthorized`
  - `422 Unprocessable Entity`

#### 5.2.2 POST /api/users/me/avatar

- **目的**: アバター画像をアップロードし、`avatarUrl` を更新
- **認証**: 必要
- **リクエスト**: `multipart/form-data`、フィールド名 `file`
  - 形式: JPEG / PNG / GIF
  - サイズ上限: 2MB
- **レスポンス** `200 OK`: 更新後の `User`
- **エラー**:
  - `401 Unauthorized`
  - `413 Payload Too Large`: 2MB 超過
  - `415 Unsupported Media Type`: 対応外の MIME

> 実装方式の検討メモ: 将来 S3 直接アップロード（pre-signed URL）に切り替える場合は `GET /api/users/me/avatar/upload-url` を別途用意する想定。MVP は単純なサーバー経由アップロードで開始する。

### 5.3 ワークスペース（F-03）

#### 5.3.1 POST /api/workspaces

- **目的**: 新規ワークスペースを作成。作成者は自動的に OWNER。既定チャンネル `general` も自動作成
- **認証**: 必要
- **リクエスト**:

```json
{
  "name": "RaiseTech",
  "description": "AI エンジニアコース上級編"
}
```

| フィールド | 制約 |
| --- | --- |
| `name` | 必須、1〜64 文字 |
| `description` | 任意、0〜255 文字 |

- **レスポンス** `201 Created`: `Workspace`
- **エラー**:
  - `401 Unauthorized`
  - `422 Unprocessable Entity`

#### 5.3.2 GET /api/workspaces

- **目的**: 自分が所属するワークスペース一覧
- **認証**: 必要
- **リクエスト**: なし（クエリパラメータなし）
- **レスポンス** `200 OK`:

```json
{
  "items": [ /* Workspace[] */ ],
  "nextCursor": null,
  "hasMore": false
}
```

ワークスペース数は通常少ないため、当面はページング不要だが共通形に揃える。

- **エラー**:
  - `401 Unauthorized`

#### 5.3.3 GET /api/workspaces/{wsId}

- **目的**: ワークスペース詳細を取得
- **認証**: 必要（当該ワークスペースのメンバーのみ）
- **リクエスト**: なし
- **レスポンス** `200 OK`: `Workspace`
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 非メンバー
  - `404 Not Found`

### 5.4 チャンネル（F-04）

#### 5.4.1 POST /api/workspaces/{wsId}/channels

- **目的**: ワークスペース内に新規チャンネルを作成
- **認証**: 必要（当該ワークスペースのメンバーのみ）
- **リクエスト**:

```json
{
  "name": "random",
  "description": "雑談",
  "type": "PUBLIC"
}
```

| フィールド | 制約 |
| --- | --- |
| `name` | 必須、1〜80 文字、ワークスペース内で一意 |
| `description` | 任意、0〜255 文字 |
| `type` | 必須、`"PUBLIC"` または `"PRIVATE"` |

- **レスポンス** `201 Created`: `Channel`。作成者は自動的に `channel_members` に追加される。
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden`
  - `409 Conflict`: 同名チャンネルが既存

#### 5.4.2 GET /api/workspaces/{wsId}/channels

- **目的**: ワークスペース内のチャンネル一覧（自分が見える範囲）
- **認証**: 必要
- **リクエスト**: クエリパラメータ
  - `type`: `"PUBLIC"` / `"PRIVATE"`（任意、フィルタ）
  - `joined`: `true` / `false`（任意、参加済みのみに絞る）
- **レスポンス** `200 OK`:

```json
{
  "items": [ /* Channel[] */ ],
  "nextCursor": null,
  "hasMore": false
}
```

仕様注: パブリックチャンネルはワークスペース全員が見える。プライベートチャンネルは自分がメンバーであるもののみ返す。

- **エラー**:
  - `401 Unauthorized` / `403 Forbidden`

#### 5.4.3 GET /api/channels/{id}

- **目的**: チャンネル詳細
- **認証**: 必要（パブリックは同一ワークスペースメンバーなら閲覧可、プライベートはメンバーのみ）
- **レスポンス** `200 OK`: `Channel`
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

#### 5.4.4 POST /api/channels/{id}/join

- **目的**: チャンネル参加
- **認証**: 必要
- **リクエスト**: なし
- **レスポンス** `200 OK`: `Channel`
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: プライベートチャンネルへ招待なしで参加しようとした
  - `404 Not Found`
  - `409 Conflict`: すでに参加済み

#### 5.4.5 POST /api/channels/{id}/leave

- **目的**: チャンネル退出（メッセージ履歴は残す。`channel_members.left_at` をセット）
- **認証**: 必要
- **レスポンス** `204 No Content`
- **エラー**:
  - `401 Unauthorized`
  - `404 Not Found`
  - `409 Conflict`: 参加していない / `general` チャンネルから退出しようとした

#### 5.4.6 DELETE /api/channels/{id}

- **目的**: チャンネル削除（論理削除）
- **認証**: 必要（OWNER または作成者のみ。詳細は F-16 / 後続 PR）
- **レスポンス** `204 No Content`
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

### 5.5 チャンネルメッセージ（F-05, F-07）

#### 5.5.1 GET /api/channels/{id}/messages

- **目的**: チャンネルのメッセージ履歴を新しい順で取得（無限スクロール）
- **認証**: 必要（当該チャンネルメンバーまたはパブリックなら同一ワークスペースメンバー）
- **リクエスト**: クエリパラメータ
  - `cursor` (任意): 前回のレスポンスの `nextCursor`
  - `limit` (任意): 既定 50、最大 100
- **レスポンス** `200 OK`:

```json
{
  "items": [ /* Message[]、createdAt 降順 */ ],
  "nextCursor": "eyJpZCI6OTAwfQ==",
  "hasMore": true
}
```

実装メモ: 直近 N 件は Redis から優先取得する（F-05 非機能要件）。具体の戦略は [docs/cache-strategy.md](cache-strategy.md) で別途定義。

- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

#### 5.5.2 PUT /api/messages/{id}

- **目的**: メッセージ本文を編集（自分のメッセージのみ。F-07）
- **認証**: 必要
- **リクエスト**:

```json
{ "body": "編集後のテキスト" }
```

| フィールド | 制約 |
| --- | --- |
| `body` | 必須、1〜4000 文字 |

- **レスポンス** `200 OK`: 編集後の `Message`（`editedAt` がセットされる）
- **副作用**: WebSocket で `/topic/channels/{id}` または `/topic/dm/{roomId}` に編集イベントを配信
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 他人のメッセージを編集
  - `404 Not Found` / `422 Unprocessable Entity`

#### 5.5.3 DELETE /api/messages/{id}

- **目的**: メッセージ削除（自分のメッセージ、または OWNER は他人も可）
- **認証**: 必要
- **レスポンス** `204 No Content`
- **副作用**:
  - メッセージを論理削除（`deleted_at` セット）。関連スレッド・リアクション・添付も論理削除
  - WebSocket で削除イベントを配信
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

### 5.6 ダイレクトメッセージ（F-06）

#### 5.6.1 POST /api/workspaces/{wsId}/dm/rooms

- **目的**: DM ルームを作成（既存ルームがあれば既存を返す）
- **認証**: 必要（自分・相手とも当該ワークスペースのメンバー）
- **リクエスト**:

```json
{ "partnerUserId": 15 }
```

- **レスポンス**:
  - `201 Created`: 新規作成
  - `200 OK`: 既存ルームを返した
  - レスポンスボディは `DmRoom`
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 相手が同一ワークスペースのメンバーでない
  - `404 Not Found`: ワークスペース不在 / 相手ユーザー不在
  - `422 Unprocessable Entity`: 自分自身を相手に指定

#### 5.6.2 GET /api/workspaces/{wsId}/dm/rooms

- **目的**: 自分が参加している DM ルーム一覧
- **認証**: 必要
- **レスポンス** `200 OK`:

```json
{
  "items": [ /* DmRoom[] */ ],
  "nextCursor": null,
  "hasMore": false
}
```

- **エラー**:
  - `401 Unauthorized` / `403 Forbidden`

#### 5.6.3 GET /api/dm/rooms/{id}/messages

- **目的**: DM のメッセージ履歴を取得（[5.5.1](#551-get-apichannelsidmessages) と同仕様）
- **認証**: 必要（当該 DM ルームメンバーのみ）
- **リクエスト**: `cursor` / `limit`
- **レスポンス** `200 OK`: ページング共通形（`items: Message[]`）
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

### 5.7 スレッド（F-08）

> 実装済み（#63）。スレッドは **1 階層に固定**（Slack セマンティクス）。返信への返信が来た場合は親をたどってスレッドの **root** に付け替える（`parentMessageId` は常に root を指す）。返信イベントは **`/topic/threads/{rootId}` のみ** に配信し、チャンネル / DM トピックには流さない（チャンネル / DM 履歴は `parent IS NULL` で返信を除外しているため、ミラー配信は再読込との不整合を生む。"also send to channel" は post-MVP）。

#### 5.7.1 GET /api/messages/{parentId}/replies

- **目的**: 親メッセージへのスレッド返信一覧
- **認証**: 必要（親メッセージのチャンネル / DM の閲覧権限を継承）
- **リクエスト**: `cursor` / `limit`
- **レスポンス** `200 OK`: ページング共通形（`items: Message[]`、`createdAt` 昇順）
  - スレッドは古い順で表示する Slack のセマンティクスに合わせる
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found`

#### 5.7.2 POST /api/messages/{parentId}/replies

- **目的**: スレッド返信を投稿
- **認証**: 必要
- **リクエスト**:

```json
{ "body": "返信本文" }
```

- **レスポンス** `201 Created`: 作成された `Message`（`parentMessageId` にスレッドの root ID がセットされる）
- **副作用**: WebSocket で `/topic/threads/{rootId}` に `MESSAGE_CREATED` イベントを配信（Redis Pub-Sub `messages:thread:{rootId}` 経由）
- **エラー**:
  - `401 Unauthorized` / `403 Forbidden` / `404 Not Found` / `422 Unprocessable Entity`

> 設計判断: 新規スレッド返信投稿は本来 WebSocket 送信が望ましいが、スレッド開始は頻度が低くクライアント実装が単純化できるため MVP では REST も用意する。チャンネル / DM のトップレベルメッセージ送信は WebSocket 一本に絞る（[6](#6-websocket-との境界) 参照）。

### 5.8 ワークスペース招待（F-15）

> 招待は「即メンバー追加」（pending 状態テーブルは作らない、`docs/database-design.md` §3.5 の方針）。チャンネル単位の招待は [5.9 チャンネル招待](#59-チャンネル招待-f-15) で別途定義する。

**トークンの扱い**: 平文トークンは発行レスポンスでのみ返す。サーバーは `SHA-256` ハッシュ（hex 64桁）のみを `workspace_invites.token_hash` に保存し、平文は保持しない。受諾時はクライアントが提示した平文を再ハッシュして照合する。

#### 5.8.1 POST /api/workspaces/{wsId}/invites

- **目的**: 招待リンクを発行する
- **認証**: 必要（当該ワークスペースの **OWNER のみ**）
- **リクエスト**（ボディ任意。省略時は既定値）:

```json
{ "expiresInHours": 24, "maxUses": 5 }
```

  - `expiresInHours`（任意, 1〜8760, 既定 168 = 7日）: 有効期限
  - `maxUses`（任意, 1〜1000, 既定 null = 無制限）: 使用回数上限
- **レスポンス** `201 Created`:

```json
{
  "id": 12,
  "workspaceId": 2,
  "token": "rawTokenString...",
  "inviteUrl": "http://localhost:3000/invite/rawTokenString...",
  "expiresAt": "2026-06-05T12:00:00+09:00",
  "maxUses": 5,
  "usedCount": 0,
  "createdAt": "2026-05-29T12:00:00+09:00"
}
```

  - `token` / `inviteUrl` はこのレスポンスでのみ取得可能（再取得不可）
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 非メンバー / 非 OWNER
  - `404 Not Found`: ワークスペース不在
  - `422 Unprocessable Entity`: `expiresInHours` / `maxUses` の範囲違反

#### 5.8.2 POST /api/invites/{token}/accept

- **目的**: 招待を受諾し、呼び出しユーザーをワークスペース（および `general` チャンネル）のメンバーにする
- **認証**: 必要（受諾するのはログイン中のユーザー本人）
- **リクエスト**: ボディなし。`token` はパス変数（発行時の平文トークン）
- **レスポンス** `200 OK`: 参加した `Workspace`
  - 既にアクティブメンバーの場合も **冪等に `200`**（`used_count` は消費しない）
  - 新規参加時は `used_count` を 1 加算し、`general` チャンネルにも参加させる
- **エラー**:
  - `401 Unauthorized`
  - `404 Not Found`: 不明なトークン / ワークスペース削除済み
  - `410 Gone`: 招待が無効化済み / 有効期限切れ / 使用上限到達

#### 5.8.3 DELETE /api/workspaces/{wsId}/invites/{inviteId}

- **目的**: 招待リンクを無効化する（`revoked_at` を設定）
- **認証**: 必要（当該ワークスペースの **OWNER のみ**）
- **レスポンス** `204 No Content`
  - 既に無効化済みでも冪等に `204`
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 非メンバー / 非 OWNER
  - `404 Not Found`: 招待が存在しない、または当該ワークスペースに属さない `inviteId`

> 設計判断:
> - **410 Gone の採用**: 期限切れ・無効化・使用上限は「リクエストは妥当だがリソースの状態で利用不可」のため、バリデーション失敗用の `422` ではなく `410` を用いる。
> - **冪等な受諾**: 招待リンクは複数回踏まれる前提（メール / チャット共有）のため、既メンバーの受諾はエラーにせず `200` を返す。
> - **使用回数の同時実行**: `used_count` は `@Transactional` 内の read-modify-write。高並行では `max_uses` をわずかに超過しうるが招待では許容（厳密化が必要なら楽観ロック / 条件付き UPDATE に切替可能）。

### 5.9 チャンネル招待（F-15）

> ワークスペース招待（5.8）の構造をチャンネル単位に写したもの。`channel_invites` テーブル（`docs/database-design.md`）に保存し、トークンの扱い・`410 Gone` の方針・冪等受諾・`used_count` の同時実行はワークスペース招待と同一。
>
> **権限モデルの差分**: ワークスペース招待が **OWNER のみ**発行・無効化できるのに対し、チャンネル招待は **当該チャンネルのアクティブメンバー**であれば発行・無効化できる。受諾するユーザーは **そのチャンネルが属するワークスペースのメンバー**でなければならない（チャンネル参加の前提）。これによりプライベートチャンネルへ既存 WS メンバーを招き入れる導線になる。

**トークンの扱い**: 平文トークンは発行レスポンスでのみ返す。サーバーは `SHA-256` ハッシュ（hex 64桁）のみを `channel_invites.token_hash` に保存し、平文は保持しない。受諾時はクライアントが提示した平文を再ハッシュして照合する。

#### 5.9.1 POST /api/channels/{id}/invites

- **目的**: チャンネルの招待リンクを発行する
- **認証**: 必要（当該チャンネルの **アクティブメンバーのみ**）
- **リクエスト**（ボディ任意。省略時は既定値。フィールド仕様は [5.8.1](#581-post-apiworkspaceswsidinvites) と同一）:

```json
{ "expiresInHours": 24, "maxUses": 5 }
```

- **レスポンス** `201 Created`:

```json
{
  "id": 7,
  "channelId": 4,
  "token": "rawTokenString...",
  "inviteUrl": "http://localhost:3000/channel-invite/rawTokenString...",
  "expiresAt": "2026-06-05T12:00:00+09:00",
  "maxUses": 5,
  "usedCount": 0,
  "createdAt": "2026-05-29T12:00:00+09:00"
}
```

  - `token` / `inviteUrl` はこのレスポンスでのみ取得可能（再取得不可）
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 当該チャンネルの非メンバー
  - `404 Not Found`: チャンネル不在
  - `422 Unprocessable Entity`: `expiresInHours` / `maxUses` の範囲違反

#### 5.9.2 POST /api/channel-invites/{token}/accept

- **目的**: 招待を受諾し、呼び出しユーザーをチャンネルのメンバーにする
- **認証**: 必要（受諾するのはログイン中のユーザー本人）
- **リクエスト**: ボディなし。`token` はパス変数（発行時の平文トークン）
- **レスポンス** `200 OK`: 参加した `Channel`
  - 既にアクティブメンバーの場合も **冪等に `200`**（`used_count` は消費しない）
  - 新規参加時は `used_count` を 1 加算する。過去に退出した行があれば `left_at` をクリアして再参加
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: チャンネルが属するワークスペースの非メンバー
  - `404 Not Found`: 不明なトークン / チャンネル削除済み
  - `410 Gone`: 招待が無効化済み / 有効期限切れ / 使用上限到達

> URL を `/api/channel-invites/{token}/accept` とし、ワークスペース招待の `/api/invites/{token}/accept`（5.8.2）と分離している。これは受諾後に参加させる対象（ワークスペース vs チャンネル）と必要な事前権限が異なるため。

#### 5.9.3 DELETE /api/channels/{id}/invites/{inviteId}

- **目的**: チャンネルの招待リンクを無効化する（`revoked_at` を設定）
- **認証**: 必要（当該チャンネルの **アクティブメンバーのみ**）
- **レスポンス** `204 No Content`
  - 既に無効化済みでも冪等に `204`
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: 当該チャンネルの非メンバー
  - `404 Not Found`: 招待が存在しない、または当該チャンネルに属さない `inviteId`

### 5.10 絵文字リアクション（F-11）

> 実装済み（#70）。任意のメッセージに標準絵文字を付与・解除する。同一ユーザー・同一 emoji の重複付与は UNIQUE 制約 `(message_id, user_id, emoji)`（`docs/database-design.md`）で防止し、API は **冪等** に振る舞う。リアクション増減はメッセージと同じトピック（スレッド返信なら `/topic/threads/{rootId}`、通常は `/topic/channels/{id}` / `/topic/dm/{roomId}`）へ WebSocket 配信する。

**`ReactionResponse` スキーマ**（1 メッセージ・1 emoji の集計）:

```json
{
  "messageId": 42,
  "emoji": "👍",
  "count": 2,
  "userIds": [1, 3]
}
```

  - `count`: その emoji の付与数 / `userIds`: 付与したユーザー ID（古い順）

#### 5.10.1 POST /api/messages/{id}/reactions

- **目的**: メッセージに絵文字リアクションを付与する
- **認証**: 必要（メッセージが属するチャンネル / DM のメンバーシップを継承）
- **リクエスト**:

```json
{ "emoji": "👍" }
```

  - `emoji`（必須, 1〜32 文字）
- **レスポンス**: `ReactionResponse`
  - 新規付与は `201 Created`、既に付与済みなら **冪等に `200 OK`**（`count` は据え置き）
- **副作用**: 新規付与時のみ `REACTION_ADDED` イベントを配信（既存付与の `200` では配信しない＝他クライアントの二重カウントを防ぐ）
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: メッセージの属するチャンネル / DM の非メンバー
  - `404 Not Found`: メッセージ不在 / 削除済み
  - `422 Unprocessable Entity`: `emoji` が空 / 32 文字超過

#### 5.10.2 DELETE /api/messages/{id}/reactions/{emoji}

- **目的**: 自分が付与したリアクションを解除する
- **認証**: 必要（付与時と同じメンバーシップ）
- **リクエスト**: ボディなし。`emoji` はパス変数（URL エンコード）
- **レスポンス** `204 No Content`
  - 付与していない emoji の解除も **冪等に `204`**
- **副作用**: 実際に削除が発生したときのみ `REACTION_REMOVED` イベントを配信
- **エラー**:
  - `401 Unauthorized`
  - `403 Forbidden`: メッセージの属するチャンネル / DM の非メンバー
  - `404 Not Found`: メッセージ不在 / 削除済み

> 設計判断:
> - **200 / 201 の切り分け**: 「既存なら 200、新規なら 201」を 1 つの POST で表現し、重複付与を `409` にしない（F-06 DM ルーム作成と同じ冪等方針）。
> - **配信は状態が変わったときだけ**: 冪等な再付与 / 未付与の解除では WS イベントを流さず、購読側のカウントがずれないようにする。
> - **配信エンベロープの共通化**: `WsEvent.payload` を `Object` 化し、メッセージ系（`MessageResponse`）とリアクション系（`ReactionResponse`）が同じ封筒・同じ Redis Pub-Sub 経路に乗るようにした。

---

## 6. WebSocket との境界

REST と WebSocket の責務分担を明示する。WebSocket メッセージプロトコルの詳細は `docs/realtime-design.md`（後続作成）で定義する。

| 操作 | 経路 | 理由 |
| --- | --- | --- |
| 認証・トークン発行 | REST | リクエスト / レスポンスが 1 回完結。WebSocket の前段 |
| メッセージ履歴取得 | REST | ページング・無限スクロール。冪等な GET でキャッシュしやすい |
| 新規メッセージ送信（チャンネル / DM） | **WebSocket のみ** | 配信経路を一本化し、リアルタイム配信時の重複・順序問題を避ける |
| メッセージ編集・削除 | **REST**（副作用として WebSocket 配信） | 操作元はクライアント単発、結果は全メンバーへブロードキャスト |
| メッセージ受信（リアルタイム） | WebSocket | サブスクライブ（`/topic/channels/{id}` 等） |
| スレッド返信投稿 | REST + WebSocket 検討 | MVP は REST。性能要件次第で WebSocket 化 |
| プロフィール・ワークスペース・チャンネル CRUD | REST | リアルタイム性が求められない |

### 6.1 WebSocket destination の現状規約（参考）

詳細は別書だが、本書の API と整合させるための先行宣言:

| destination | 用途 |
| --- | --- |
| `/topic/channels/{id}` | チャンネル新規メッセージ・編集・削除・リアクション増減イベントの配信先 |
| `/topic/dm/{roomId}` | DM 新規メッセージ・編集・削除・リアクション増減イベントの配信先 |
| `/topic/threads/{parentId}` | スレッド返信・スレッド内リアクション増減イベントの配信先 |
| `/app/channels/{id}/messages` | クライアント → サーバー：チャンネルメッセージ送信 |
| `/app/dm/{roomId}/messages` | クライアント → サーバー：DM メッセージ送信 |

---

## 7. 今後の追加機能（F-09 以降の予告）

本書はコア機能（F-01〜F-08）に絞った。残機能の API は別 PR で追補する。概略:

| 機能 | 想定エンドポイント | 備考 |
| --- | --- | --- |
| F-09 マークダウン | サーバー側エンドポイントなし | クライアント側レンダリングのみ。XSS 対策は表示時のサニタイズ |
| F-10 ファイル添付 | `POST /api/messages/{id}/attachments` または S3 pre-signed URL | ファイル方式の決定が前提 |
| F-11 絵文字リアクション | ✅ [§5.10](#510-絵文字リアクションf-11) で定義済（付与・解除 + WS 配信） | |
| F-12 メンション | メッセージ送信時に本文パースで自動抽出。明示エンドポイントは不要 | 通知は F-14 と連動 |
| F-13 検索 | `GET /api/workspaces/{wsId}/search?q=...` | PostgreSQL `body_tsv` を使った全文検索 |
| F-14 通知 | `GET /api/notifications`, `PUT /api/read-states` 等 | 未読カウントは Redis 主、`read_states` はソース |
| F-15 招待 | ✅ ワークスペース招待を [§5.8](#58-ワークスペース招待f-15) で定義済。チャンネル招待（`POST /api/channels/{id}/invites`）は別 Issue | |
| F-16 管理者操作 | 既存 CRUD の権限拡張で対応 | 専用エンドポイントは最小限 |

---

## 8. 変更履歴

| 日付 | 変更内容 | PR |
| --- | --- | --- |
| 2026-05-28 | 初版作成（F-01〜F-08 のコア機能）| #23 |
| 2026-05-29 | F-15 ワークスペース招待 API（発行・受諾・無効化）を §5.8 に追加。410 Gone を §2.3 に追加 | #59 |
| 2026-05-29 | F-08 スレッド API（§5.7）を実装。1 階層固定（root 付け替え）/ 返信イベントは `/topic/threads/{rootId}` のみに配信、を実装メモとして追記 | #63 |
| 2026-05-30 | F-11 絵文字リアクション API（§5.10）を実装。付与 201/200 冪等・解除 204 冪等、`WsEvent.payload` を `Object` 化してリアクションイベントを既存トピックへ配信 | #70 |
