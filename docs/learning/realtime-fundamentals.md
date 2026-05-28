# 学習ノート — リアルタイム通信の基礎（D-2 振り返り）

関連設計書: [docs/realtime-design.md](../realtime-design.md)
関連ノート: [docs/learning/api-design-fundamentals.md](api-design-fundamentals.md)

このノートは PR #28（D-2 WebSocket プロトコル設計書）のマージ後に行った振り返り学習をチートシート形式で残したもの。
**前提知識ゼロから読める** 順序で、HTTP / WebSocket / STOMP / destination / broker / Pub-Sub / Redis Pub-Sub / JWT 認証 / 責務分担を扱う。

---

## 0. このノートの位置づけ

| ファイル | 目的 | 読む順 |
| --- | --- | --- |
| `docs/realtime-design.md` | **決定事項**: リアルタイム通信プロトコルを記述 | 仕様を確認したい時 |
| `docs/learning/realtime-fundamentals.md` (本書) | **なぜそう決めたか**: 前提知識と設計判断の根拠 | 仕様の背景を理解したい時 |
| `docs/learning/api-design-fundamentals.md` | REST API 設計の基礎（D-1 振り返り） | REST 側の前提知識 |

D-1 学習ノートを先に読んでおくと、本書の「REST との比較」がスムーズ。

---

## 1. 通信の前提（HTTP のおさらい）

### 1.1 HTTP のひと言

**HTTP** = ブラウザとサーバーが話す時のお作法。性質は 1 つだけ:

> **お願い 1 回 → 返事 1 回で終わり。サーバーから話しかけられない。**

```
[ブラウザ] ── お願い ──▶ [サーバー]
[ブラウザ] ◀── 返事 ──── [サーバー]
                終わり
```

### 1.2 チャットアプリで HTTP だけだと困る理由

チャットの体験は「**他人が投稿したら、自分が何も操作しなくても画面に出てくる**」こと。
HTTP だけだと、こちらから「新しいメッセージある?」と何度も聞く必要がある（= ポーリング、無駄が多い・遅い）。

→ **サーバー側から能動的にプッシュできる仕組み** が必要。それが WebSocket。

---

## 2. WebSocket — 常時つなぎっぱなしの双方向通信路

### 2.1 ひと言

**WebSocket** = ブラウザとサーバーが **常時接続したまま、どちらからでも話せる** 通信路。

```
[ブラウザ] ◀═══════════════▶ [サーバー]
       接続後はどちらからでも喋れる
```

### 2.2 HTTP との比較

| 観点 | HTTP | WebSocket |
| --- | --- | --- |
| 通信モデル | お願い→返事の片道 1 往復 | 接続後はどちらからでも送れる |
| サーバーから話しかけられる? | ❌ | ✅ |
| 接続時間 | 1 リクエストで終わり | 切断するまで継続 |
| 用途 | 普通のページ取得・API | チャット・通知・株価ティッカー |

### 2.3 WebSocket は「通信路」だけ提供する

ここが大事。WebSocket は **接続路を作るだけ** のプロトコル。
そこに何を流すか（JSON か / 中身の構造）は **何も決まっていない**。

例えば「メッセージ送信」と「タイピング通知」を区別したいと思ったら、自分でフォーマットを決める必要がある:

```json
{ "kind": "msg", "text": "..." }
{ "kind": "typing", "user": 12 }
```

これをチーム内で勝手に決めて開発すると、再利用性も標準ツールの恩恵もない。
→ **WebSocket の上に共通フォーマット** を被せたい。それが STOMP。

---

## 3. STOMP — WebSocket の上のメッセージのお作法

### 3.1 ひと言

**STOMP** = Simple Text Oriented Messaging Protocol
= WebSocket の上で動く「**メッセージのお作法**」。

> **どこ宛 / どんな種類 のメッセージか** を表す共通フォーマットを提供する。

### 3.2 STOMP の見た目（frame と呼ぶ）

1 通のメッセージを **フレーム** と呼ぶ。テキストでこんな形:

```
SEND
destination:/app/channels/5/messages
content-type:application/json

{"body":"おはようございます"}
^@
```

| 行 | 意味 |
| --- | --- |
| 1 行目 | コマンド（種類）。`SEND` = サーバーに送る |
| 2–3 行目 | ヘッダ。`destination` で宛先を指定 |
| 空行のあと | 本文（ペイロード） |
| `^@` | フレーム終端マーカー |

**「コマンド + ヘッダ + 本文」** のシンプル構成。HTTP リクエストとよく似ている。

### 3.3 STOMP の主要コマンド

| コマンド | 方向 | 意味 |
| --- | --- | --- |
| `CONNECT` | クライアント → サーバー | 「接続します。私は誰々です」 |
| `CONNECTED` | サーバー → クライアント | 「接続 OK」 |
| `SEND` | クライアント → サーバー | 「このメッセージをこの宛先に送って」 |
| `SUBSCRIBE` | クライアント → サーバー | 「この宛先のメッセージが来たら教えて」 |
| `MESSAGE` | サーバー → クライアント | 「あなたが購読してた宛先に配信です」 |
| `ERROR` | サーバー → クライアント | 「エラーです」 |
| `DISCONNECT` | クライアント → サーバー | 「切断します」 |

### 3.4 階層イメージ

```
┌──────────────────────────────────────┐
│ STOMP（メッセージの構造ルール）       │  ← どの宛先に / どんな種類か
├──────────────────────────────────────┤
│ WebSocket（常時接続の通信路）         │  ← データを流すパイプ
├──────────────────────────────────────┤
│ TCP（インターネットの土管）           │
└──────────────────────────────────────┘
```

WebSocket がパイプ、STOMP がそのパイプに流す **荷札付きの封筒**。

---

## 4. destination — 振り返りで一番詰まった概念

ここは重点解説。**比喩なし・具体シナリオで** 追う。

### 4.1 まず「destination は何ではない」を消す

- ❌ **URL ではない**（HTTP の URL は「サーバー上のリソースの場所」だが、destination は違う）
- ❌ **ファイルパスでもない**（対応する実体はサーバーのどこにも存在しない）

destination は **ただの仕分け用文字列ラベル**。

### 4.2 状況設定（3 人のチャットを想像してください）

- **あなた**（ID 12）が `#general`（channel ID 5）を開いている
- **Alice**（ID 15）も同じ `#general` を開いている
- **Bob**（ID 20）は別の `#random`（channel ID 7）を開いている

3 人とも WebSocket でサーバーに接続済み:

```
    あなた ════════╗
                   ║
    Alice  ════════╠═══ サーバー
                   ║
    Bob    ════════╝
```

### 4.3 問題提起

**あなたが「おはよう」と送ったとき、サーバーは誰に届ければいい?**

- Alice には届けるべき（同じ `#general`）
- Bob には届けるべきでない（別チャンネル）

ここで疑問: **サーバーはどうやって「誰がどのチャンネルを見ているか」を知る?**

WebSocket は「3 本の接続が刺さってる」しか知らない。3 人が今どのチャンネルを開いてるかは **接続だけでは分からない**。

### 4.4 解決: クライアントが事前に申告する

クライアントが **「私は今 #general を見てます」とサーバーに申告** しておく。
サーバーは申告を覚えておいて、メッセージが届いた時に申告者に配る。

この「申告」を表現するのに STOMP は **`SUBSCRIBE` という専用フレーム** を用意している:

```
SUBSCRIBE
destination:/topic/channels/5

^@
```

### 4.5 destination の正体

> **destination** = クライアントが SUBSCRIBE フレームで「これを購読します」と申告するときの **申告先を表す文字列**

`/topic/channels/5` という文字列自体には **物理的な実体はない**。チームで決めた **命名規則のラベル**。

### 4.6 RaiseChat のシナリオに当てはめる

3 人がやることは:

| 誰 | サーバーに送る申告 |
| --- | --- |
| あなた（#general 開く時）| `SUBSCRIBE destination:/topic/channels/5` |
| Alice（#general 開く時）| `SUBSCRIBE destination:/topic/channels/5` |
| Bob（#random 開く時）| `SUBSCRIBE destination:/topic/channels/7` |

サーバーは申告を受け取ると、内部にこんな表（**名簿**）を作る:

```
destination          │ 購読中の session
─────────────────────┼─────────────────────────
/topic/channels/5    │ [あなた, Alice]
/topic/channels/7    │ [Bob]
```

これで「あなたが `/topic/channels/5` 宛にメッセージ送ったとき、Alice には届けて Bob には届けない」という振り分けができる。

### 4.7 送信側の destination は別文字列（`/app/**`）

ここが振り返りで一番ハマった箇所。**購読用と送信用で文字列が違う**。

| 方向 | 使う prefix |
| --- | --- |
| クライアント **→** サーバー（投げる）| **`/app/...`** |
| サーバー **→** クライアント（受け取る）| **`/topic/...`** |

**あなた** が「おはよう」と送る STOMP フレーム:

```
SEND
destination:/app/channels/5/messages

{"body":"おはよう"}
^@
```

### 4.8 なぜ `/app/` と `/topic/` を分けるのか

ここが核心。Spring Boot のサーバー内部は **2 つの部品** に分かれている:

```
┌────────────────────────────────────────────┐
│       サーバー（Spring Boot プロセス）       │
│                                             │
│   ┌──────────────┐     ┌──────────────┐    │
│   │ Java ハンドラ │     │   broker     │    │
│   │ 自分が書くコード│     │ 名簿を持って │    │
│   │ (DB 保存とか) │     │ 配るだけの部品│    │
│   └──────────────┘     └──────────────┘    │
└────────────────────────────────────────────┘
```

| 部品 | 役割 |
| --- | --- |
| **Java ハンドラ** | 自分で書く `@MessageMapping` メソッド。**処理を挟みたい時に通す** |
| **broker** | Spring が用意した「購読名簿持って配るだけ」の部品。**処理を挟まずに右から左に流す** |

STOMP の振り分けルール（Spring の設定で決定）:

| destination の prefix | フレームの行き先 |
| --- | --- |
| `/app/...` | **Java ハンドラ** 行き |
| `/topic/...` | **broker** 直行（Java は 1 行も動かない） |
| `/queue/...` | **broker** 直行（個人宛）|

#### もしクライアントが `/topic/channels/5` に直接 SEND したら

```
クライアント ─SEND /topic/channels/5─▶ broker
                                       │
                                       ▼
                          購読者全員に流れる
                          （Java コードは動かない）

→ DB に保存されない
→ 認可チェックなし
→ メンバー外の Bob でも投稿できてしまう ❌
```

`/topic/**` は **検証ゼロでブロードキャストしてしまう高速道路**。

#### だから `/app/**` を経由させる

```
クライアント ─SEND /app/channels/5/messages─▶ Java ハンドラ
                                              │
                                              ├ DB 保存
                                              ├ 認可チェック
                                              │
                                              ▼
                                  broker に「配信して」と命令
                                              │
                                              ▼
                                  /topic/channels/5 の購読者に配る
```

**ひと言で:**

> **`/topic/**` は broker 直行の高速道路。`/app/**` は Java を通る一般道。**
> **検証が要る操作は必ず一般道（`/app/`）を通す。**

### 4.9 サーバー側コード（イメージ）

REST の `@PostMapping` と同じノリ:

```java
// REST: HTTP POST /api/users が来たら呼ばれる
@PostMapping("/api/users")
public User createUser(...) { ... }

// WebSocket: /app/channels/5/messages が SEND で来たら呼ばれる
@MessageMapping("/app/channels/{channelId}/messages")
public void handle(@DestinationVariable Long channelId, MessageRequest req) {
    Message saved = messageService.save(channelId, req);     // DB 保存
    broker.convertAndSend("/topic/channels/" + channelId, saved);  // 配信指示
}
```

`broker.convertAndSend(destination, body)` が **「broker さん、これをこの destination の購読者全員に配って」** という命令。

### 4.10 destination のまとめ

- destination = **「これを購読します」「ここに送ります」とサーバーに伝える文字列ラベル**
- サーバーの内部には **「destination → 購読 session の名簿」** がある
- 送信用 destination（`/app/**`）は **Java ハンドラ行き**
- 配信用 destination（`/topic/**`）は **broker 直行**で購読者全員に届く
- 文字列の中身は **チームが決めた命名規則** に過ぎない

---

## 5. broker と Pub-Sub

### 5.1 broker のひと言

**broker** = STOMP の **中央郵便局**。仕事は 2 つだけ:

1. **「destination → 購読してる session の名簿」** を内部に持つ
2. 「この destination に MESSAGE を流して」と頼まれたら、**名簿を引いて該当 session 全員に流す**

### 5.2 絵で見ると

```
┌─────────────────────────────────────────────┐
│            broker (中央郵便局)                │
│                                               │
│  名簿(in-memory map):                          │
│  ┌─────────────────────────────────────────┐ │
│  │ destination          │ 購読中 session    │ │
│  │ ─────────────────────┼─────────────────  │ │
│  │ /topic/channels/5    │ [あなた, Alice]   │ │
│  │ /topic/channels/7    │ [Bob]             │ │
│  └─────────────────────────────────────────┘ │
│                                               │
│  受付窓口:                                     │
│  「/topic/channels/5 に流して」               │
│   と頼まれる → 名簿引いて [あなた, Alice] へ│
└─────────────────────────────────────────────┘
```

### 5.3 broker が動く瞬間

```
あなた SEND /app/channels/5/messages
    │
    ▼
Java ハンドラ (DB 保存・認可チェック)
    │
    │ broker.convertAndSend("/topic/channels/5", message)
    ▼
broker
    │ 名簿引く → [あなた, Alice]
    ▼
MESSAGE フレームを 2 session に flush
```

### 5.4 Pub-Sub という名前

「1 つの destination に publish したら、その destination を subscribe してる **全員に配る**」という配信パターンを **Pub-Sub**（Publish-Subscribe）と呼ぶ。

| 用語 | 意味 |
| --- | --- |
| **publish** | broker に「この destination に流して」と頼む側 |
| **subscribe** | broker に「この destination 聞きます」と申告する側 |

**broker は Pub-Sub を成立させる部品**。

### 5.5 RaiseChat が選んだ broker

Spring の WebSocket では broker を 2 種類から選べる:

| 種類 | 特徴 |
| --- | --- |
| **simple broker** | Spring **プロセス内** で動く。軽量。設定 1 行で済む |
| **StompBrokerRelay** | RabbitMQ などの外部 broker に丸投げ。インフラ追加が必要 |

RaiseChat MVP は **simple broker** を採用（[realtime-design.md §2.3](../realtime-design.md)）。
理由: 既に Redis を別目的で立てる予定があり、追加コンポーネントを増やしたくない。

---

## 6. 複数インスタンス問題と Redis Pub-Sub

### 6.1 「インスタンス」という言葉

**インスタンス** = 同じ Spring Boot アプリのプロセス 1 つ。
本番では負荷分散・冗長化（1 台落ちても他が生きている）のため、同じアプリを **複数台動かす** のが普通。
これを「**複数インスタンス**」と呼ぶ。

```
        ロードバランサー(振り分け係)
              │
   ┌──────────┴──────────┐
   ▼                     ▼
Spring Boot #1      Spring Boot #2
  (同じアプリの実行プロセス)
```

クライアントが接続してきたら、**ロードバランサーが #1 か #2 にランダムで振り分ける**。

### 6.2 シナリオ拡張

3 人をこの構成に乗せると:

```
あなた  ════▶ Spring Boot #1
Alice   ════▶ Spring Boot #2  ← Alice だけ #2 に飛ばされた
Bob     ════▶ Spring Boot #1
```

3 人とも `#general`（channel ID 5）を SUBSCRIBE 済み。

### 6.3 問題: あなたの「おはよう」が Alice に届かない

```
あなた SEND /app/channels/5/messages
   │
   ▼
Spring Boot #1 の Java ハンドラ (DB 保存・認可)
   │
   ▼
#1 の broker.convertAndSend("/topic/channels/5", ...)
   │
   ▼
#1 の broker、名簿を引く
   │
   ▼
名簿: /topic/channels/5 → [あなた, Bob]
                            └─ Alice は #2 に繋がってるので #1 の名簿に載ってない ❌
   │
   ▼
あなた・Bob には届く / Alice には届かない ❌
```

### 6.4 原因

> **simple broker は「自分のプロセス内の名簿」しか見えない。**

#1 の broker と #2 の broker は **お互いの存在を知らない**。これが simple broker の代償。

### 6.5 解決: 伝言役を間に置く

#1 と #2 が「お互いを知らない」のが問題なら、**間に伝言役を置けばいい**。
この伝言役が **Redis Pub-Sub**。

### 6.6 Redis Pub-Sub のひと言

**Redis** = よくキャッシュで使われるインメモリ DB。
**Pub-Sub 機能** = Redis に標準で付いてる「メッセージ配信機能」。

使う Redis コマンドは 2 つ:

| Redis コマンド | 意味 |
| --- | --- |
| `PUBLISH <チャネル名> <メッセージ>` | 「このチャネルにメッセージ投げます」 |
| `SUBSCRIBE <チャネル名>` | 「このチャネル聞きます」 |

> ⚠️ ここで「**チャネル**」という単語が出てきますが、**Slack のチャンネルとは別物**です。
> Redis Pub-Sub の「チャネル」は **Redis 内部の伝言ルートの名前**。本書では「Redis チャネル」と呼びます。

### 6.7 動きの絵（複数インスタンス対応版）

```
あなた SEND /app/channels/5/messages
   │
   ▼
Spring Boot #1 が処理
   │
   ├─① 自分の broker に流す → #1 の名簿: [あなた, Bob] に配信  ✅
   │
   └─② Redis に PUBLISH (チャネル名: ws:workspace:1)
           │
           │ Redis Pub-Sub 経由で全インスタンスに配信
           │
        ┌──┴────────────────┐
        ▼                    ▼
    #1 が SUBSCRIBE     #2 が SUBSCRIBE
    (自分が送ったので     (受け取った! 自分の名簿を引く)
    重複を避けるため     名簿: /topic/channels/5 → [Alice]
    無視 or スキップ)    → Alice に MESSAGE 配信  ✅
```

これで Alice にも届く ✅。

### 6.8 Redis チャネル名の粒度

「どの粒度で Redis チャネルを切るか」には 3 案あった:

| 案 | チャネル例 | 評価 |
| --- | --- | --- |
| A. 全部 1 本 | `ws:all` | 雑だが実装最簡単。関係ないインスタンスにも届く |
| B. **ワークスペース単位** | `ws:workspace:1` | **MVP のバランス点。RaiseChat 採用** |
| C. destination 単位 | `ws:topic:channels:5` | 最も無駄ないがチャネル数が爆発 |

→ RaiseChat は **B（ワークスペース単位）** を採用（[realtime-design.md §7.2](../realtime-design.md)）。

### 6.9 ハイブリッド構成のまとめ

```
[クライアント] ⇔ STOMP over WebSocket ⇔ [Spring Boot]
                                            │
                          ┌── simple broker (自プロセス内配信)
                          │
                          └── Redis Pub-Sub (他インスタンスへの橋渡し)
```

- **インスタンス内**の配信 → simple broker
- **インスタンス間**の橋渡し → Redis Pub-Sub

---

## 7. JWT を STOMP CONNECT に載せる理由

### 7.1 REST での認証のおさらい

REST API（C タスクで実装したやつ）は、リクエスト時にこう送る:

```http
GET /api/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

`Authorization` という HTTP ヘッダに JWT を載せる、ごく普通の方式。

### 7.2 WebSocket でも同じことをしたい、けど…

WebSocket でも「私が誰か」をサーバーに伝えないと、認可（誰がどの destination を購読できるか）が判定できない。
同じく `Authorization` ヘッダで…と思いたいが、**ブラウザ仕様の壁** にぶつかる。

### 7.3 ブラウザ標準 WebSocket API の制約

ブラウザの JavaScript で WebSocket を開くコードはこう:

```javascript
const ws = new WebSocket("wss://example.com/ws");
```

このとき、**カスタム HTTP ヘッダ（`Authorization` を含む）は設定できない仕様**。
`new WebSocket()` には URL しか渡せず、ヘッダオプションがない。

> Node.js のサーバーサイドライブラリだと付けられるが、**ブラウザは無理**。

→ WebSocket のハンドシェイク HTTP リクエスト時に Authorization ヘッダで認証はできない。

### 7.4 どこに JWT を載せるか?（3 案）

| 案 | 内容 | 評価 |
| --- | --- | --- |
| A. URL クエリ | `wss://.../ws?token=eyJ...` | トークンがアクセスログ・ブラウザ履歴に残る → ❌ |
| B. Cookie | JWT を Cookie 保存 → 自動送信 | CSRF 対策必要・既存 REST が Bearer 方式なので二重管理 → △ |
| C. **STOMP CONNECT フレームのヘッダ** | WebSocket 接続後の最初のフレームに載せる | クリーン・既存 Bearer と統一可能 → ✅ |

### 7.5 STOMP CONNECT フレームに載せる（C 採用）

STOMP の接続シーケンス:

```
1. WebSocket ハンドシェイク (HTTP → WebSocket への昇格、ヘッダなし)
2. クライアントが STOMP CONNECT フレームを送る ← ここで認証
3. サーバーが STOMP CONNECTED で応答
```

CONNECT フレームは **任意のヘッダを付けられる**:

```
CONNECT
accept-version:1.2
host:raisechat.example.com
Authorization:Bearer eyJhbGciOiJIUzI1NiIs...

^@
```

### 7.6 サーバー側処理（[realtime-design.md §3.2](../realtime-design.md)）

```
①WebSocket 接続が確立 (ハンドシェイクは認証なしで通過)
       │
       ▼
②クライアントから CONNECT フレームが届く
       │
       ▼
③Spring の ChannelInterceptor が CONNECT フレームのヘッダから JWT を取り出す
       │
       ▼
④既存の JwtTokenProvider (REST と共通) で検証
       │
       ├ 成功 → CONNECTED を返し、session に Principal を紐付け
       └ 失敗 → ERROR フレームを返して切断
```

ポイント: **REST で使ってる `JwtTokenProvider` をそのまま流用** できる。トークン体系を二重管理しないで済む。

### 7.7 ひとことで

> ブラウザ仕様で `Authorization` ヘッダが付けられない → STOMP の **CONNECT フレームのヘッダに載せる**。これで REST と同じ JWT・同じ検証ロジックが使える。

---

## 8. 共通エンベロープ

### 8.1 WebSocket が REST と決定的に違う性質

**1 つの destination に複数種類のメッセージが流れる**。

| | REST | WebSocket |
| --- | --- | --- |
| URL / destination の粒度 | **1 URL = 1 操作**（`PUT /api/messages/{id}` は編集だけ） | **1 destination に複数種類**（`/topic/channels/5` に新規・編集・削除・リアクション・タイピングが全部流れる） |
| 操作の見分け方 | HTTP メソッド + URL で自明 | **メッセージ本文を見ないと分からない** |

例えば `/topic/channels/5` を購読中のクライアントには:

```
12:00: 新規メッセージ「おはよう」
12:05: 誰かが編集
12:06: 誰かが 👍 リアクション
12:07: 誰かがタイピング開始
12:08: 削除イベント
```

これを **クライアント側で見分けて UI を出し分け** ないといけない。

### 8.2 共通エンベロープのアイデア

全部のメッセージを **同じ封筒** に入れて、封筒の表に「種類」を書く:

```json
{
  "type": "message.created",
  "serverTime": "2026-05-28T12:00:00Z",
  "payload": { /* 種類ごとに違うデータ */ }
}
```

| フィールド | 役割 |
| --- | --- |
| `type` | **必須**。`message.created` / `message.updated` / `reaction.added` / `typing.started` ... と文字列で種類を示す |
| `serverTime` | サーバー配信時刻（ISO 8601 UTC） |
| `payload` | 中身。種類ごとに形が違う |

クライアントは **`type` を見るだけで分岐できる**:

```javascript
ws.onMessage((envelope) => {
  switch (envelope.type) {
    case "message.created": addMessageToUI(envelope.payload); break;
    case "message.updated": updateMessageInUI(envelope.payload); break;
    case "reaction.added":  addReactionToUI(envelope.payload); break;
    // ...
  }
});
```

### 8.3 RaiseChat のイベント種別一覧

`<リソース>.<動詞>` の形に揃えた:

```
message.created    新規メッセージ
message.updated    編集
message.deleted    削除
reaction.added     リアクション追加
reaction.removed   リアクション解除
typing.started     タイピング開始
typing.stopped     タイピング停止
presence.changed   オンライン状態変化
read.updated       既読位置更新
notification.*     通知系
```

### 8.4 ひとことで

> WebSocket は **1 destination に複数種類のイベントが流れる** ので、**共通封筒** で包んで `type` で見分けるのが定石。

---

## 9. 送信は WebSocket / 編集・削除は REST という責務分担

### 9.1 検討した 3 案

| 案 | 送信 | 編集・削除 |
| --- | --- | --- |
| X. 全部 REST | REST | REST |
| Y. 全部 WebSocket | WebSocket | WebSocket |
| **Z（採用）** | **WebSocket** | **REST + WebSocket 配信** |

### 9.2 案 X（全部 REST）が没な理由

REST だけでは **リアルタイム配信できない**（HTTP の片方向性、§1.1）。
非機能要件「配信遅延 1 秒以内」を満たすには WebSocket が必須。

### 9.3 案 Y（全部 WebSocket）が没な理由

#### ① 編集・削除の「失敗を確実に伝える」が苦手

REST は **リクエスト 1 回 → レスポンス 1 回** で結果が明確:

```
PUT /api/messages/1001 → 200 OK + 編集後 Message
PUT /api/messages/1001 → 403 Forbidden (他人のメッセージ)
PUT /api/messages/1001 → 404 Not Found
```

WebSocket は broker 経由で **publish したら投げっぱなし**。
「あなたの編集が成功したか?」を返すには共通エンベロープにエコー用の `clientMessageId` を混ぜて自前検出が必要。
編集・削除のような「1 回限り・結果が重要」な操作には REST が圧倒的に楽。

#### ② 認可エラーの返し方

WebSocket のエラーは STOMP ERROR フレーム or `/user/queue/errors` 経由で、REST の `403 Forbidden` ほど直感的でない。

#### ③ 新規送信を WebSocket 一本にすると「配信経路の二重化」を避けられる（採用の決め手）

もし送信が REST と WebSocket 両方で受けられると、サーバーは:
- REST で受けた送信 → DB 保存 → broker.convertAndSend で `/topic/...` に配信
- WebSocket で受けた送信 → DB 保存 → broker.convertAndSend で `/topic/...` に配信

の **2 系統のコード** を持つことになる。
Redis Pub-Sub が絡むと、経路が増えるほど **重複配信・順序逆転のリスク** が膨らむ。

新規送信を WebSocket 一本に絞れば:
- 配信経路が **1 系統だけ**
- `clientMessageId` で送信元エコーを 1 通りに検出できる
- Redis 経由の重複検出も 1 種類で済む

#### ④ レイテンシ

WebSocket は既に接続済みなので、送信のたびに HTTP ハンドシェイクをやり直さない。新規メッセージは高頻度なのでこれが効く。

### 9.4 採用案 Z の動き（全用語が登場）

**新規送信（F-05）:**

```
あなた SEND /app/channels/5/messages  ← §4.7 の送信用 destination
   │
   ▼
Spring Boot #1 の Java ハンドラ  ← §4.8 の "/app は一般道"
   ├ JWT 認証済 (§7)
   ├ DB 保存
   │
   ▼
broker.convertAndSend("/topic/channels/5", envelope)  ← §5 broker + §8 エンベロープ
   │
   ├ #1 内 → /topic/channels/5 の名簿に配信 (§5)
   └ Redis に PUBLISH ws:workspace:1 (§6)
              │
              ▼
      #2 の Listener → #2 の broker → Alice に配信
```

**編集・削除（F-07）:**

```
あなた PUT /api/messages/1001  ← REST, Authorization: Bearer JWT
   │
   ▼
Spring Boot の RestController
   ├ JWT 検証 (REST/WebSocket 共通の JwtTokenProvider, §7)
   ├ DB 更新 (editedAt セット)
   │
   ▼
200 OK + 更新後 Message  ← あなたに即レスポンス、エラーは ProblemDetail
   │
   │ (副作用として)
   ▼
broker.convertAndSend("/topic/channels/5", { type: "message.updated", ... })
   │
   ├ #1 内 → Bob に配信
   └ Redis 経由 → #2 → Alice に配信
```

### 9.5 ひとことで

> **新規送信は「リアルタイム配信」が主目的なので、配信路と一体の WebSocket に一本化。**
> **編集・削除は「操作結果を確実に伝える」が主目的なので REST で受け、配信は WebSocket に任せる。**
> 「操作の入口」と「配信の出口」を意識的に分けたのが採用案 Z。

---

## 10. チートシート

### 10.1 用語ひとこと辞典

| 用語 | ひと言 |
| --- | --- |
| HTTP | 「お願い 1 回 → 返事 1 回」の片方向通信 |
| WebSocket | 常時接続の双方向通信路（通信路だけ提供） |
| STOMP | WebSocket の上のメッセージのお作法（コマンド + ヘッダ + 本文） |
| frame | STOMP の 1 通のメッセージ単位 |
| destination | 申告先・配信先を表す文字列ラベル（実体はない） |
| Java ハンドラ | 自分で書く `@MessageMapping` メソッド（処理を挟む経路） |
| broker | 「destination → 購読 session の名簿」を持って配る部品 |
| Pub-Sub | 1 つに publish → 購読者全員に配る配信モデル |
| simple broker | Spring プロセス内で動く broker |
| インスタンス | 同じアプリのプロセス 1 つ |
| Redis Pub-Sub | インスタンス間の伝言役（Redis の機能） |
| JWT | 「私は誰々」をサーバーが署名付きで証明したトークン |
| エンベロープ | `{type, serverTime, payload}` の共通封筒 |

### 10.2 destination の方向

| 方向 | prefix | 行き先 |
| --- | --- | --- |
| クライアント → サーバー（投げる）| **`/app/...`** | Java ハンドラ |
| サーバー → クライアント（配信）| **`/topic/...`** | broker 直行 → 購読者全員 |
| サーバー → クライアント（個人宛）| **`/user/queue/...`** | broker 直行 → 該当ユーザーのみ |

### 10.3 「あなた → Alice」全経路

```
あなた → /app/channels/5/messages（SEND）
       → Spring Boot #1 の Java ハンドラ
         ├ DB 保存
         ├ 認可チェック
         └ broker.convertAndSend("/topic/channels/5", envelope)
              ├ #1 内: 名簿 [あなた] へ配信
              └ Redis PUBLISH ws:workspace:1
                  └→ #2 の Listener → #2 の broker → Alice へ
```

### 10.4 設計判断早見表

| 論点 | 採用 | 理由ひとこと |
| --- | --- | --- |
| 接続パス | `/ws` + SockJS | 単一で十分。SockJS は旧環境フォールバック |
| 認証 | STOMP CONNECT フレームに JWT | ブラウザの WebSocket API がカスタムヘッダ非対応 |
| broker | simple broker + Redis Pub-Sub | 軽量。RabbitMQ を別に立てなくて済む |
| Redis チャネル粒度 | ワークスペース単位 `ws:workspace:{id}` | 全部 1 本は雑、destination 単位は爆発、中間がベスト |
| メッセージ送信 | WebSocket 一本（REST 提供せず）| 配信経路の二重化を防ぐ |
| 編集・削除 | REST + WebSocket 配信 | 結果応答が要る操作は REST が向く |
| エンベロープ | `{type, serverTime, payload}` | 1 destination に複数種類が流れるので `type` で分岐 |

### 10.5 詰まりやすいポイント TOP 3

1. **destination は URL ではない**。実体がない、ただの文字列ラベル
2. **`/app/` と `/topic/` は方向が逆**。クライアント送信 = `/app/`、サーバー配信 = `/topic/`
3. **simple broker は自プロセス内しか見えない**。だから Redis Pub-Sub が橋渡しする

---

## 関連

- [docs/realtime-design.md](../realtime-design.md) — 設計書本体（決定事項）
- [docs/api-design.md](../api-design.md) — REST API 設計書
- [docs/learning/api-design-fundamentals.md](api-design-fundamentals.md) — REST 側の前提知識
- [docs/learning/auth-jwt.md](auth-jwt.md) — JWT 認証 API の実装解説
