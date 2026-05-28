# Redis キャッシュ戦略設計書 — RaiseChat（key 設計 / TTL / 無効化）

関連: [要件定義書](requirements.md) / [機能要件書](functional-requirements.md) / [API 設計書](api-design.md) / [リアルタイム通信設計書](realtime-design.md) / [データベース設計書](database-design.md)

---

## 0. はじめに

### 0.1 本書のゴール

RaiseChat の **Redis をキャッシュとして使うときの方針** を確定させる。
具体的には以下を決める:

- 何をキャッシュし、何をキャッシュしないか
- キャッシュ key の命名規則
- どの TTL を当てるか
- いつキャッシュを無効化（削除 or 更新）するか
- Redis ダウン時にどう振る舞うか
- 同じ Redis を Pub-Sub（[D-2](realtime-design.md)）と共有してよいか

### 0.2 本書のスコープ

| 範囲 | 状態 |
| --- | --- |
| キャッシュ対象一覧（F-01〜F-14 を辿る） | ✅ 本書で定義 |
| key 命名規則 / 名前空間 | ✅ 本書で定義 |
| TTL 設計 | ✅ 本書で定義 |
| 無効化（invalidation）トリガ | ✅ 本書で定義 |
| Redis Pub-Sub との共存ルール | ✅ 本書で定義（D-2 の §7.5 引き継ぎ） |
| 障害時のフォールバック方針 | ✅ 本書で定義 |
| cache stampede の方針 | ✅ 本書で定義（MVP では許容を明記） |
| 監視・メトリクスの方針 | ✅ 概念レベルで定義 |

### 0.3 本書のスコープ外

| トピック | 担当ドキュメント |
| --- | --- |
| Redis を **Pub-Sub として使う** チャネル設計 | [docs/realtime-design.md §7](realtime-design.md) |
| REST API の URL・スキーマ | [docs/api-design.md](api-design.md) |
| テーブル定義・インデックス | [docs/database-design.md](database-design.md) |
| 実装コード（`RedisTemplate` の設定や `@Cacheable` の使い方）| 実装フェーズ（本書は仕様のみ） |
| 監視ダッシュボードの具体構成（Grafana 等）| インフラフェーズ |

### 0.4 本書を読む前提

- DB（PostgreSQL）が **ソース・オブ・トゥルース**。Redis は派生キャッシュであり、消えても再構築できる（[database-design.md §1 原則 5](database-design.md)）
- 認証 / API 設計（[D-1](api-design.md)）と WebSocket 設計（[D-2](realtime-design.md)）は既に確定済
- Redis インスタンスは 1 つ（docker-compose の `redis:7` コンテナ）を **キャッシュと Pub-Sub で共有する**

---

## 1. 用語定義

専門用語を使う前にまとめて定義する。本書中で初出した用語が分からなくなったらここに戻ること。

| 用語 | ひと言で | 補足 |
| --- | --- | --- |
| **キャッシュ (cache)** | 「もう一度問い合わせると遅いもの」を **手前にコピーして置いておく** 場所 | RaiseChat では Redis 上のキー / 値ペアを指す。本物は常に DB |
| **key / value** | Redis に保存する **キーと値のペア** | key は文字列。value は文字列・数値・リスト・セット・ハッシュなど複数の型を取れる |
| **TTL** (Time To Live) | キーの **有効期限**（秒）。期限が切れると自動的に消える | `EXPIRE key 60` で 60 秒後に消える |
| **eviction**（追い出し） | Redis のメモリが足りなくなったとき、設定ポリシーに従って **古い key を勝手に消す** こと | `maxmemory-policy` 設定で決まる（LRU / LFU など） |
| **cache-aside**（lazy loading）| **アプリ側が**「まずキャッシュを見る → なければ DB → 取れた値をキャッシュに保存」を自分で書く方式 | 一番素直。RaiseChat の基本戦略 |
| **read-through** | キャッシュ層が自前で DB から読みに行く方式（アプリは Cache の API だけを叩く）| Spring の `@Cacheable` が近いが、明示的な無効化と相性が悪いので RaiseChat では使わない |
| **write-through** | 書き込み時に **DB と キャッシュの両方** を同時に更新する方式 | 一貫性が強いが実装が複雑。今回は採らない |
| **write-behind** | 書き込みは **キャッシュにだけ反映** し、DB へは非同期で遅延書き込み | 整合性リスクが高いので採らない |
| **無効化 (invalidation)** | 「キャッシュの中身が古くなったので捨てる」操作 | DELETE で消す方式と、新しい値で上書きする方式がある。RaiseChat は基本「DELETE で消すだけ、次に必要になったとき作り直す」 |
| **cache stampede**（雪崩、thundering herd）| 人気のある key が TTL 切れになった瞬間、**大量のリクエストが同時にキャッシュミス**して DB に殺到する現象 | 対策は SETNX ロック・Probabilistic Early Recomputation など。MVP では許容 |
| **hit / miss** | キャッシュにあった = hit、なかった = miss | hit 率（hit / (hit+miss)）が低いとキャッシュの存在意義が薄い |
| **Pub-Sub** | Redis の **配信機能**（PUBLISH / SUBSCRIBE） | キャッシュとは **別の機能**。本書では基本扱わない（[D-2](realtime-design.md) の責務） |
| **論理 DB index** | 1 つの Redis インスタンス内で **0〜15 の番号付き名前空間** を切れる機能 | `SELECT 0` / `SELECT 1` で切り替え。本書では採らず prefix で分離する（§2.4） |

---

## 2. 全体方針

### 2.1 なぜキャッシュするか

RaiseChat には以下のような「読みが多く、書きが少なく、再計算可能」なデータがある。これらを毎回 DB から取り直すのは無駄。

| 例 | 読み頻度 | 書き頻度 | 取り直しコスト |
| --- | --- | --- | --- |
| ワークスペースのメンバー一覧 | 高（権限チェック毎回） | 低（参加・退出のときだけ） | JOIN クエリ |
| チャンネル一覧 | 高（サイドバー表示毎に） | 低（作成・削除のときだけ） | 単純 SELECT だが回数が多い |
| 直近メッセージ N 件 | 非常に高（チャンネル切り替え毎に） | 高だが追記専用 | カーソルクエリ |
| 未読数 | 非常に高（バッジ表示） | 非常に高（新着・既読） | COUNT クエリ × 全チャンネル |
| プレゼンス（オンライン状態）| 高 | 中（接続/切断/アイドル） | DB に保存していない（揮発性） |

加えて、Redis キャッシュ戦略は本コース（RaiseTech AI 上級編）の **学習テーマの 1 つ**。実装することで「いつキャッシュが効くか・効かないか」「無効化はなぜ難しいか」を体験する目的がある。

### 2.2 何をキャッシュし、何をキャッシュしないか

| キャッシュする | キャッシュしない |
| --- | --- |
| 読み >> 書き なもの | 書きが主体のもの（メッセージ本体） |
| 再計算 / 再取得可能なもの | 整合性が厳格に必要なもの（金銭・在庫等は本アプリにない） |
| 単一クエリの結果（メンバー一覧、チャンネル一覧）| 全文検索結果（F-13 は DB の `to_tsvector` 等で直接、キャッシュは将来検討）|
| 揮発性で OK なもの（プレゼンス・未読カウント）| 認証クリティカルな決定（JWT 検証は署名検証で完結。ブラックリストのみキャッシュ）|

> メッセージ本体（`messages` テーブル）はキャッシュ「しない」が、**直近メッセージの ID リスト** はキャッシュする（[§4.2](#42-直近メッセージ-id-リスト-f-05-中核)）。

### 2.3 採用する戦略: cache-aside を基本とする

RaiseChat は **cache-aside（lazy loading）** を基本戦略とする。

```
GET の擬似コード:
  value = redis.get(key)
  if value is None:
      value = db.query(...)
      redis.set(key, value, ttl)
  return value

UPDATE の擬似コード:
  db.update(...)
  redis.del(key)         # 削除のみ。次の GET で作り直される
```

採用理由:

| 戦略 | 採用 | 理由 |
| --- | --- | --- |
| cache-aside | ✅ 採用 | 一番シンプル。Redis ダウン時のフォールバックも書きやすい（read 側で try-catch するだけ） |
| read-through (`@Cacheable` 全自動) | ❌ | 無効化のタイミングを明示しづらい。RaiseChat は WebSocket イベントと連動して invalidate したいので、暗黙化したくない |
| write-through | ❌ | 二重書き込みで複雑化。失敗時のロールバック設計が重い |
| write-behind | ❌ | DB がソース・オブ・トゥルースという原則（[B 設計](database-design.md)）に反する |

例外: **カウンタ系（未読数）** は cache-aside ではなく **直接 Redis の INCR / DECR で更新** する。理由は §4.3 で説明する。

### 2.4 Pub-Sub との共存ルール（D-2 §7.5 引き継ぎ）

[D-2 §7.5](realtime-design.md) で「Pub-Sub と通常 KV ストアを同一 Redis でどう分けるか」を D-3 に引き継いでいた。本書での結論:

| 候補 | 採否 | 理由 |
| --- | --- | --- |
| A. 別の Redis インスタンスを立てる | ❌ | 運用コスト増。MVP では避ける |
| B. 同一インスタンス + 論理 DB index で分離（`SELECT 0=cache, 1=pubsub`）| ❌ | クライアントごとに「どっちの index か」を都度切り替えるのが煩雑。設定ミス時に検知しづらい |
| C. **同一インスタンス + key prefix で分離** | ✅ **採用** | 設定ファイルで prefix を強制すれば衝突しない。視認性も良い |

prefix のルール:

| prefix | 用途 | 例 |
| --- | --- | --- |
| `cache:` | 派生キャッシュ（DB から再構築可） | `cache:user:123` |
| `unread:` | 未読カウンタ（カウンタ系で別 prefix） | `unread:user:12:channel:5` |
| `presence:` | オンライン状態 | `presence:user:12` |
| `jwt:` | JWT 関連（ブラックリスト等） | `jwt:blacklist:abcd1234` |
| `rate:` | レート制限カウンタ | `rate:login:ip:192.168.1.1` |
| `lock:` | 分散ロック（将来用） | `lock:invite:abc` |
| `ws:` | **Redis Pub-Sub チャネル**（[D-2 §7.2](realtime-design.md) で確定） | `ws:workspace:5` |

> `ws:` は Pub-Sub の `PUBLISH` / `SUBSCRIBE` チャネル名であり、通常の GET / SET の key とは別レイヤだが、prefix を分けておくと監視や `KEYS` でのフィルタリングが楽になる。

---

## 3. key 命名規則

### 3.1 基本パターン

```
<namespace>:<resource>:<id>[:<sub-resource>[:<id>]]
```

- すべて小文字
- 区切りは `:`（Redis 慣習）
- 単数形（`user` であって `users` ではない）
- ID は数値（[D-1 §1 原則 6](api-design.md) と一致）

### 3.2 例

| key | 意味 |
| --- | --- |
| `cache:user:123` | ユーザー 123 のプロフィール情報 |
| `cache:user:123:workspaces` | ユーザー 123 が所属するワークスペース ID 一覧 |
| `cache:workspace:5:members` | ワークスペース 5 のメンバー ID 一覧 |
| `cache:workspace:5:channels` | ワークスペース 5 のチャンネル ID 一覧 |
| `cache:channel:10:members` | チャンネル 10 のメンバー ID 一覧 |
| `cache:channel:10:recent` | チャンネル 10 の直近メッセージ ID 一覧 |
| `unread:user:12:channel:5` | ユーザー 12 のチャンネル 5 における未読数 |
| `presence:user:12` | ユーザー 12 のオンライン状態 |
| `jwt:blacklist:<jti>` | ログアウト済みアクセストークンの JTI |
| `rate:login:ip:<ip>` | IP 別ログイン試行回数 |

### 3.3 バージョニング

将来キャッシュ value の構造を変えるとき、古い key が残っていると壊れる。対策は以下のいずれか:

| 案 | 採否 |
| --- | --- |
| prefix にバージョンを入れる（`cache:v1:user:123`）| ❌ MVP では入れない（過剰最適化） |
| 構造変更時に全 key を一括 FLUSH | ✅ 採用（運用手順で対応。MVP は再デプロイ時に DB 再構築する想定なので困らない） |

---

## 4. キャッシュ対象一覧

### 4.1 一覧表

| # | 対象 | key | Redis 型 | TTL | 無効化トリガ | 関連機能 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ユーザー情報 | `cache:user:{id}` | Hash | 1h | プロフィール更新 | F-02 |
| 2 | ユーザー所属ワークスペース一覧 | `cache:user:{id}:workspaces` | Set | 1h | ワークスペース参加 / 退出 / キック | F-03 |
| 3 | ワークスペースメンバー一覧 | `cache:workspace:{id}:members` | Set | 1h | 参加 / 退出 / キック | F-03, F-16 |
| 4 | ワークスペース内チャンネル一覧 | `cache:workspace:{id}:channels` | Set | 1h | チャンネル作成 / 削除 | F-04 |
| 5 | チャンネルメンバー一覧 | `cache:channel:{id}:members` | Set | 1h | チャンネル参加 / 退出 / キック | F-04 |
| 6 | 直近メッセージ ID リスト | `cache:channel:{id}:recent` | List | 24h | 新規 / 編集 / 削除 | F-05, F-07 |
| 7 | 未読カウント | `unread:user:{userId}:channel:{channelId}` | Integer | なし（明示更新のみ） | 新規メッセージで INCR / 既読で 0 にリセット | F-14 |
| 8 | プレゼンス | `presence:user:{userId}` | String | 90s（ハートビート式）| WebSocket 接続でセット、自動失効 | F-14 補助 |
| 9 | JWT ブラックリスト | `jwt:blacklist:{jti}` | String | トークン残存時間 | ログアウト時に SET | F-01 |
| 10 | ログイン試行レート制限 | `rate:login:ip:{ip}` | Integer | 60s | 自動失効 | F-01 補助 |

以後、特に設計上の判断が必要なもの（直近メッセージ・未読カウント・プレゼンス・JWT ブラックリスト）を個別に詳述する。単純な cache-aside で済むもの（#1〜#5）は表の通り。

### 4.2 直近メッセージ ID リスト（F-05 中核）

**目的**: チャンネルを開いた瞬間に表示する「直近 N 件」を高速化する。

**設計**:

- key: `cache:channel:{id}:recent`
- 型: Redis **List**（`LPUSH` で先頭追加、`LRANGE 0 49` で取得）
- value: メッセージ ID（数値）のみを保存。**本体は DB から ID で個別取得**
- 上限: 50 件（`LTRIM 0 49` で先頭 50 件にトリム）
- TTL: 24 時間

**なぜ ID だけ？ 本体を入れない理由**:

| 案 | 採否 | 理由 |
| --- | --- | --- |
| 本体（JSON 文字列）をリストに直接入れる | ❌ | メッセージ編集 / 削除のたびにリスト全走査が必要。著者プロフィール変更時も古い情報が残る |
| **ID リスト + 個別取得**（採用）| ✅ | リストは軽量。本体は `cache:message:{id}` で別途キャッシュする手もあるが MVP では DB 直接で十分。ID リストの長さが固定なので無効化も楽 |

**読み取りフロー**:

```
1. ids = LRANGE cache:channel:{id}:recent 0 49
2. ids が空（miss） → DB から SELECT ... ORDER BY id DESC LIMIT 50
                       → LPUSH で投入、LTRIM 0 49、EXPIRE 86400
3. ids がある → DB から WHERE id IN (...) で本体取得（深い履歴は別途 cursor で DB 直接）
```

**新規メッセージ送信時**:

```
INSERT INTO messages ...
LPUSH cache:channel:{id}:recent <new_id>
LTRIM cache:channel:{id}:recent 0 49
EXPIRE cache:channel:{id}:recent 86400
```

**編集 / 削除時**:

ID リストは変わらないが、表示時に DB から最新本体を引くため、ID リストの再生成は **不要**。
ただし削除（論理削除）後に「削除済みメッセージ」を含む ID が残る場合があり、表示側で `deleted_at IS NULL` を確認する。

> 直近メッセージ「本体」のキャッシュ（`cache:message:{id}`）は MVP では作らない。必要性が見えてから追加する。

### 4.3 未読カウント（F-14）

**目的**: サイドバーの未読バッジを高速に出す。

**設計**:

- key: `unread:user:{userId}:channel:{channelId}`
- 型: Integer（`INCR` / `DECR` / `SET 0`）
- TTL: **なし**（明示更新のみ。eviction でも消えていいよう、消えた時は DB の `read_states` から再構築する）

**なぜ cache-aside ではないか**:

未読カウントは「**今までの累積からの差分**」であって、毎回 DB で `COUNT(*) WHERE channel_id=X AND id > read_state.last_read_id` するのは高頻度では重い。
そのため Redis 側を **正本に近い形** で運用する:

- 新規メッセージ送信時: チャンネルメンバー全員分の `unread:user:*:channel:{id}` を `INCR`（送信者本人は除く）
- 既読更新時: `SET unread:user:{自分}:channel:{id} 0` + DB の `read_states.last_read_id` を更新

DB の `read_states` は最終既読位置を保持しており、Redis が飛んだら再計算可能。

**懸念点（明記して残す）**:

- メンバー多数のチャンネル（例: ワークスペース全員参加の `general`）で 1 投稿が大量の INCR を呼ぶ → MVP では許容、性能課題が見えたら **未読は「件数」ではなく「last_read_id との差」だけ持つ** 方式に切り替える

### 4.4 プレゼンス（F-14 補助）

**目的**: 「誰がオンラインか」を全ユーザーに見せる。

**設計**:

- key: `presence:user:{userId}`
- 型: String（値は `online` / `away`、`offline` は **キーがないこと** で表現）
- TTL: 90 秒（ハートビート式）

**ハートビートの仕組み**:

- WebSocket 接続中、クライアントは 30 秒ごとに `/app/heartbeat` を送る
- サーバーは `SET presence:user:{id} online EX 90` を打つ
- 90 秒以内に次のハートビートが来なければ自動失効 → そのユーザーは offline 扱い
- 状態が変化したら [D-2 の `presence.changed`](realtime-design.md) を WebSocket 配信

**なぜ DB に保存しないか**:

- 揮発性で構わない（再起動で全員 offline 表示 → 次の接続で復活）
- DB には書きたくない更新頻度

### 4.5 JWT ブラックリスト（F-01）

**目的**: ログアウトしたアクセストークンを **期限前に無効化** できるようにする。

**設計**:

- key: `jwt:blacklist:{jti}`
- 型: String（値は何でも良い。`"1"` 等）
- TTL: **そのトークンの残り有効期間**（`SET jwt:blacklist:<jti> 1 EX <exp - now>`）

**フロー**:

- ログアウト時: クライアントから受け取ったアクセストークンをパースし、`jti` と `exp` を取り出す → 上記 SET
- API 認証時: JWT 検証通過後、`GET jwt:blacklist:{jti}` で存在チェック。あれば 401

**注意**:

- リフレッシュトークンは DB の `refresh_tokens` テーブルで管理（既存実装）。アクセストークンだけ Redis を併用する
- TTL がトークン期限と一致するので、自動的に掃除される（メモリ肥大しない）

### 4.6 ログイン試行レート制限（F-01 補助）

**目的**: パスワード総当たり攻撃の抑止。

**設計**:

- key: `rate:login:ip:{ip}`
- 型: Integer
- TTL: 60 秒
- 上限: 1 分で 10 試行（仮）

**フロー**:

```
count = INCR rate:login:ip:{ip}
if count == 1:
    EXPIRE rate:login:ip:{ip} 60
if count > 10:
    return 429 Too Many Requests
```

> MVP では IP ベースのみ。ユーザー ID ベースのレート制限は後続検討。

---

## 5. TTL 設計

### 5.1 TTL の決め方

TTL を決めるときの基本原則:

1. **整合性が緩くて良いもの**: 長めの TTL（1 時間〜24 時間）
2. **整合性が厳しいもの**: 明示無効化に頼り、TTL は安全網（1 時間〜なし）
3. **揮発性で良いもの**（プレゼンス・レート制限）: 短い TTL（60〜90 秒）で自動失効
4. **トークン期限が決まっているもの**: その期限と一致

### 5.2 TTL ポリシー一覧

| 区分 | TTL 例 | 当てはまる対象 |
| --- | --- | --- |
| **短命** | 60〜90s | レート制限、プレゼンス |
| **中** | 1h | ユーザー情報、ワークスペース / チャンネル一覧、メンバー一覧 |
| **長** | 24h | 直近メッセージ ID リスト |
| **トークン連動** | トークン残存時間 | JWT ブラックリスト |
| **TTL なし** | – | 未読カウント（明示更新のみ。Redis 永続化なし運用なので再起動で消える前提） |

### 5.3 TTL 切れ時に起きること（明示する）

| 対象 | TTL 切れ後 | 影響 |
| --- | --- | --- |
| ユーザー情報・各種一覧 | 次の read で DB から再生成 | レイテンシが一時的に増えるだけ |
| 直近メッセージ ID リスト | 次の read で DB から再生成 | 同上。ただし高頻度 key なので [§9 cache stampede](#9-cache-stampede-対策の方針) の対象 |
| プレゼンス | offline 扱いに変わる | 仕様通り（ハートビートが来ていない＝離脱） |
| JWT ブラックリスト | トークン自体も期限切れになっている | 問題なし |

---

## 6. 無効化（invalidation）戦略

### 6.1 基本方針: 「更新」ではなく「削除」

cache-aside の文脈で書き込み時にキャッシュをどう扱うかは 2 通りある:

| 方式 | 採否 | 理由 |
| --- | --- | --- |
| 削除（DELETE）| ✅ **採用** | 次の read で DB から作り直されるので、間に DB トリガ・他の書き込みが挟まっても自然に整合する |
| 更新（SET 新しい値）| ❌ | 同時複数書き込み時に「古い値が後から SET される」競合が起きる |

例外:

- **カウンタ系**（未読数）は INCR / DECR で直接更新する（§4.3）
- **JWT ブラックリスト**は SET（追加であって更新ではない）

### 6.2 無効化トリガ表

API イベント / WebSocket イベントごとに、消す key を定義する。実装時の「キャッシュ消し忘れバグ」を防ぐためのチェックリストになる。

| 起点イベント | 操作 | 影響を受ける key |
| --- | --- | --- |
| プロフィール更新 (`PUT /api/users/me`) | DEL | `cache:user:{自分のid}` |
| ワークスペース作成 (`POST /api/workspaces`) | DEL | `cache:user:{作成者id}:workspaces` |
| ワークスペース参加 (招待受諾) | DEL | `cache:user:{参加者id}:workspaces` / `cache:workspace:{wsId}:members` |
| ワークスペース退出 / キック | DEL | 同上 |
| ワークスペース削除 | DEL | `cache:workspace:{wsId}:*` を全パターン削除 / 影響メンバーの `cache:user:{id}:workspaces` |
| チャンネル作成 (`POST /api/workspaces/{wsId}/channels`) | DEL | `cache:workspace:{wsId}:channels` |
| チャンネル削除 | DEL | `cache:workspace:{wsId}:channels` / `cache:channel:{id}:*` を全削除 |
| チャンネル参加 / 退出 | DEL | `cache:channel:{id}:members` |
| **メッセージ送信** (WebSocket) | LPUSH + INCR | `cache:channel:{id}:recent` に追加 / 全メンバー（送信者除く）の `unread:user:*:channel:{id}` を INCR |
| メッセージ編集 (`PUT /api/messages/{id}`) | （何もしない or 安全側で DEL） | `cache:channel:{id}:recent` は ID リストなので影響なし。本体キャッシュは MVP では存在しないので操作不要 |
| メッセージ削除 (`DELETE /api/messages/{id}`) | （同上） | `cache:channel:{id}:recent` 上では論理削除済 ID が残るが、表示側で `deleted_at` を見るため OK |
| 既読更新 (`POST /api/channels/{id}/read`) | SET 0 | `unread:user:{自分のid}:channel:{id}` を 0 |
| ログアウト (`POST /api/auth/logout`) | SET | `jwt:blacklist:{jti}`（残り有効期間 TTL） |
| WebSocket 接続 / ハートビート | SET EX | `presence:user:{userId}` |

### 6.3 cache-aside の参照フロー（疑似コード）

```python
def get_workspace_members(workspace_id):
    key = f"cache:workspace:{workspace_id}:members"
    members = redis.smembers(key)
    if not members:
        members = db.query("SELECT user_id FROM workspace_members WHERE workspace_id=?", workspace_id)
        if members:
            redis.sadd(key, *members)
            redis.expire(key, 3600)  # 1h
    return members
```

### 6.4 書き込み時の擬似コード（順序が重要）

```python
def kick_user_from_workspace(workspace_id, user_id):
    # 1. DB を先に更新
    db.execute("DELETE FROM workspace_members WHERE workspace_id=? AND user_id=?", workspace_id, user_id)

    # 2. キャッシュ DEL は DB 更新の「後」
    redis.delete(f"cache:workspace:{workspace_id}:members")
    redis.delete(f"cache:user:{user_id}:workspaces")
```

> **DB → Redis の順序を守る**。逆だと、間に他リクエストが入って古い値を再 SET する競合が起きる。

---

## 7. Pub-Sub との連携（複数インスタンス時の整合）

### 7.1 何が問題になりうるか

[D-2](realtime-design.md) で Spring Boot を複数台にする想定がある。素朴な疑問:

> インスタンス A が DB を更新して `cache:user:123` を DELETE した。
> でもインスタンス B が **直前にこの key を読んでメモリに持っていた** ら、古い値を使い続けないか？

### 7.2 結論: 問題は起きない（Redis を共有しているため）

| 観点 | 答え |
| --- | --- |
| Redis インスタンスは A も B も同じか？ | はい（docker-compose / 本番でも 1 つの Redis を共有） |
| キャッシュは Redis に **だけ** 置いているか？ | はい。JVM プロセス内に長期保持しない |
| → A が DEL したら B も次に GET したとき miss して DB へ取りに行く | はい。これで自動整合 |

つまり **cache-aside + 共有 Redis** の組み合わせで、インスタンス間整合は自動的に解決される。

### 7.3 ではキャッシュ無効化のために Pub-Sub を使う場面はあるか

MVP では **使わない**。
ただし将来 JVM プロセス内に local cache（Caffeine 等）を入れて二段キャッシュにする場合、local cache の同期に Pub-Sub が必要になる。本書では後続検討項目として [§11](#11-todo--未確定事項) に記録するに留める。

### 7.4 同一 Redis 共有時の注意

- prefix を [§2.4](#24-pub-sub-との共存ルールd-2-75-引き継ぎ) のルールで分離している限り、Pub-Sub の `PUBLISH ws:workspace:5 ...` とキャッシュの `GET cache:workspace:5:members` は **同名衝突しない**
- Redis の `KEYS *` を本番で打たないこと（O(N) で全 key 走査）。監視は `SCAN` を使う

---

## 8. 障害時の挙動

### 8.1 Redis ダウン時

| 機能 | フォールバック |
| --- | --- |
| ユーザー情報 / 各種一覧 | DB 直接（レイテンシは劣化するが API は動く） |
| 直近メッセージ | DB 直接 |
| 未読カウント | 一時的に **未読数表示を諦める**（バッジは出ない or `-` 表示）。新規メッセージ通知は WebSocket 経由で別途届く |
| プレゼンス | 全員 offline 表示 |
| JWT ブラックリスト | **チェックをスキップ**（=ログアウトしたトークンが期限まで生きる）。セキュリティ的に許容範囲。アクセストークンは短命（15 分等）にしておく |
| レート制限 | スキップ（=制限なし） |

### 8.2 実装上の注意

- Redis 操作は **try-catch** で囲み、失敗時はログ出力のみで例外を上位に投げない
- Spring Data Redis の `RedisConnectionFailureException` を専用ハンドラで握る
- Circuit Breaker（Resilience4j 等）は MVP では入れない（後続検討）

### 8.3 キャッシュ汚染（不正データが入った）

| 対策 | 採否 |
| --- | --- |
| TTL で時間制限 | ✅ 採用（全 key に TTL を持たせる方針が §5 で確定済） |
| 手動 FLUSH 手順を運用ドキュメントに用意 | ✅ デプロイ手順書に記載予定 |
| 検証用ハッシュを value に持たせる | ❌ オーバーエンジニアリング |

---

## 9. cache stampede 対策の方針

### 9.1 何が問題か

人気の高い key が TTL 切れになった瞬間、同時アクセス中のリクエスト全部が miss → 全部が DB に殺到する。`cache:channel:{id}:recent` が代表例。

### 9.2 対策案

| 案 | 採否 |
| --- | --- |
| A. SETNX による分散ロック（1 リクエストだけ DB 取得、他は待つ）| ❌ MVP では採らない |
| B. Probabilistic Early Recomputation（XFetch アルゴリズム）| ❌ MVP では採らない |
| C. TTL を長め（24h）にして頻繁に切れないようにする | ✅ 採用 |
| D. **MVP では許容**（性能劣化が見えたら導入を検討）| ✅ 採用 |

理由: MVP のユーザー規模では同時 miss の発生確率が低く、最初から複雑化するメリットが薄い。性能テストで顕在化したら A を入れる。

---

## 10. 監視・メトリクス

### 10.1 観測したい指標

| 指標 | なぜ見たいか |
| --- | --- |
| **hit rate / miss rate** | キャッシュが効いているかの基本指標。50% を下回るならキャッシュ設計を疑う |
| **eviction 数** | メモリが足りていないサイン |
| **メモリ使用量** | 上限到達前に警告したい |
| **コマンド別レイテンシ** | 重いコマンド（KEYS 等）が混入していないか |
| **接続数** | コネクションプールの異常検知 |

### 10.2 取得手段（方針のみ）

| ツール | 用途 |
| --- | --- |
| Spring Boot Actuator + Micrometer | アプリ側から hit/miss を計測（`@Cacheable` 経由でないので、自前カウンタが必要）|
| Redis `INFO` コマンド / RedisInsight | Redis サーバー自身の統計（メモリ・eviction・接続）|
| Grafana ダッシュボード | 可視化（インフラフェーズで設計）|

MVP では Actuator のメトリクスエンドポイントを開けるところまで。本格的なダッシュボードは後続。

---

## 11. ToDo / 未確定事項

本書のスコープでは確定しなかった項目。後続フェーズや実装中に決める。

| 項目 | いつ決めるか |
| --- | --- |
| 直近メッセージ「本体」のキャッシュ（`cache:message:{id}`）導入可否 | 性能テスト後 |
| 未読カウントを「件数」から「last_read_id 差分」方式に切り替えるか | 大量メンバーチャンネルが負荷になったとき |
| cache stampede の本格対策（SETNX ロック等） | 性能テストで stampede が観測されたとき |
| JVM プロセス内 local cache（Caffeine）の二段化と Pub-Sub 連携 | 単一 Redis のレイテンシが問題になったとき |
| 監視ダッシュボード（Grafana） | インフラフェーズ |
| Redis Cluster 化 / Sentinel | 冗長化フェーズ |
| key バージョニング規約（構造変更時の運用） | 構造変更が必要になったとき |
| 全文検索結果（F-13）のキャッシュ可否 | F-13 実装時 |
