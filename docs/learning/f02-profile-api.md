# 学習ノート — F-02 プロフィール API（PR #40 振り返り）

関連設計書: [docs/api-design.md](../api-design.md) §5.2 / §2.2
関連実装: [PR #40](https://github.com/gitmrk2k6/RaiseChat/pull/40)
関連ノート: [docs/learning/api-design-fundamentals.md](api-design-fundamentals.md) / [docs/learning/jpa-entity-mapping.md](jpa-entity-mapping.md)

このノートは PR #40（F-02 プロフィール更新 API）のマージ後に行った振り返り学習を、**設計判断とパターン引き出し**として残したもの。Spring / Java の細かい syntax は省き、**「次に AI に何を指示するか、その指示が正しいか判断できる力」**を養う観点で再構成している。**前提知識ゼロから読める**順序を意識した。

---

## 0. このノートの位置づけ

| ファイル | 目的 | 読む順 |
| --- | --- | --- |
| `docs/api-design.md` §5.2 | **決定事項**: F-02 プロフィール API の仕様 | 仕様を確認したい時 |
| `docs/learning/f02-profile-api.md` (本書) | **なぜそう決めたか / 引き出し**: 設計判断の根拠と再利用可能なパターン | 次に同種の API を作るとき、AI に指示するとき |

---

## 1. なぜエラー JSON を標準化するのか — RFC 7807 ProblemDetail

### 1.1 そもそも HTTP / JSON はエラー JSON の形を決めていない

世の中の HTTP API が返すエラー JSON の形は、**「決まっていない」のが標準状態**。理由は単純で:

| 仕様 | 何を決めているか | エラー JSON の形は決めているか |
| --- | --- | --- |
| **HTTP** | ステータスコード、ヘッダ、ボディが存在することなど | **決めていない**（バイト列でありさえすればよい） |
| **JSON** | `{}` `[]` の文法、文字列・数値の書き方 | **決めていない**（文法が合えば中身は自由） |

つまりエラー JSON の中身は、各 API の設計者が好き勝手に決めてよい。

### 1.2 有名 API のバラバラ実例

```jsonc
// GitHub
{ "message": "Validation Failed", "documentation_url": "..." }

// Stripe
{ "error": { "type": "card_error", "code": "...", "message": "..." } }

// Google
{ "error": { "code": 400, "message": "...", "errors": [...] } }

// 私たちの auth（PR #40 時点の旧形式）
{ "message": "validation failed", "errors": { "userId": "..." } }
```

全部「エラー JSON」だが、キー名も `errors` の形（配列 vs オブジェクト）もバラバラ。フロントエンドは API ごとに「この API のエラーはこう読む」という個別知識を持つ必要がある。

### 1.3 RFC 7807 ProblemDetail = 共通形式の提案

このバラバラを整理するために提案された **共通形式の標準仕様**が **RFC 7807**。「RFC」はインターネット技術の通し番号で、7807 番がエラー JSON 形式の仕様書。RFC 7807 が定めた JSON の形を **ProblemDetail**(問題の詳細）と呼ぶ。

PR #40 で F-02 が返すエラーレスポンス（E2E で実際に取得した実物）:

```json
{
  "type": "https://raisechat.example.com/problems/validation",
  "title": "Validation Failed",
  "status": 422,
  "detail": "リクエストボディに不正な値があります",
  "instance": "/api/users/me",
  "errors": [
    { "field": "displayName", "message": "displayName は 1〜32 文字" }
  ]
}
```

### 1.4 ProblemDetail の標準 5 フィールド

| フィールド | 意味 | 性質 |
| --- | --- | --- |
| `type` | エラー種別を識別する URI | 「種類」（同じ種類なら同じ値） |
| `title` | 人間が読む短い説明 | 「種類」 |
| `status` | HTTP ステータスコード | 「種類」（ヘッダと一致） |
| `detail` | より詳しいメッセージ | 「今回の発生状況」 |
| `instance` | エラーが起きたリクエスト URI | 「今回の発生状況」 |

→ `type` / `title` / `status` が「**エラーの種類**」、`detail` / `instance` が「**今回の具体的な発生状況**」を表す。

### 1.5 拡張フィールド (`errors`)

RFC 7807 は標準 5 つ以外に **「自分で好きなフィールドを追加してよい」と明示的に許している**。これを **拡張フィールド**(extension members) と呼ぶ。

F-02 では「**どのフィールドが** 何で失敗したか」をフロントが表示できるよう、`errors` 配列を拡張フィールドとして追加した:

```json
"errors": [
  { "field": "displayName", "message": "displayName は 1〜32 文字" },
  { "field": "statusMessage", "message": "statusMessage は 0〜100 文字" }
]
```

→ **複数フィールドが同時にエラーになっても配列で全部返せる**。フロントは配列を回して、対応する入力欄の下に赤字を出せばよい設計。

### 持ち帰る 1 行

> 「エラー JSON の形は HTTP / JSON が決めていないので**世の中バラバラが標準状態**。RFC 7807 ProblemDetail は **共通形式の提案**で、F-03 以降の RaiseChat の API は全部この形に揃える」

---

## 2. HTTP レスポンスの構造と Content-Type の役割

ProblemDetail を「**どう宣言するか**」が次のテーマ。鍵は Content-Type ヘッダ。

### 2.1 HTTP レスポンスは「ヘッダ + ボディ」

E2E で 422 を返したときの生のレスポンス:

```
HTTP/1.1 422                                       ← ステータス行
X-Content-Type-Options: nosniff                    ┐
Content-Type: application/problem+json             │ ← ヘッダ部分（メタ情報）
Transfer-Encoding: chunked                         │
Date: Thu, 28 May 2026 12:30:09 GMT                ┘
                                                   ← 空行（ヘッダ終了の合図）
{"type":"...","title":"Validation Failed",...}     ← ボディ部分（実データ）
```

- **ヘッダ部分** = レスポンスのメタ情報を `名前: 値` 形式で並べたもの
- **ボディ部分** = 実際に送りたいデータ本体

### 2.2 Content-Type ヘッダの役割

ヘッダの中の `Content-Type` は **「ボディに入っているデータがどんな形式か」**を示す。受け取り手はこれを見ないと、JSON としてパースしていいか、HTML として描画すべきか、画像として表示すべきか判断できない。

| Content-Type の値 | ボディの中身 |
| --- | --- |
| `text/html` | HTML |
| `application/json` | 通常の JSON |
| `application/problem+json` | RFC 7807 ProblemDetail 形式の JSON |
| `image/png` | PNG 画像のバイナリ |
| `multipart/form-data` | フォーム送信（ファイル添付付き） |

### 2.3 `application/problem+json` という標準形式宣言

`application/problem+json` の **`+json`** サフィックスは「中身は JSON だが、特殊なスキーマに従う」ことを示す業界慣習。

| | ボディの内容 | Content-Type | 意味 |
| --- | --- | --- | --- |
| 成功 (200) | User スキーマの JSON | `application/json` | ただの JSON |
| エラー (422) | ProblemDetail 形式の JSON | `application/problem+json` | RFC 7807 ProblemDetail に従う JSON |

ボディは **両方とも JSON**。違うのは **「貼られたラベル」**（Content-Type）だけ。サーバーが「私はこの形式で書きました」と宣言し、クライアントが「あ、その形式ね」と確信を持って読める仕組み。

### 2.4 なぜステータスコードと Content-Type の両方が必要か

レスポンスを受け取ったとき、クライアントが知りたい情報は 2 つ:

| 質問 | どこに書いてある？ |
| --- | --- |
| ① 成功？それともエラー？ | **HTTP ステータスコード** |
| ② ボディはどんな形の JSON？ | **Content-Type ヘッダ** |

ステータスだけでは「エラーだ」までしか分からない。**Content-Type が「だから body は ProblemDetail の形で読んでよい」を保証する**。

擬似コードで書くと:

```js
if (res.status >= 400) {                                    // ① エラーかどうか
  if (res.headers.get("Content-Type")
        === "application/problem+json") {                   // ② どう読むか
    const problem = await res.json();
    problem.errors.forEach(e =>
      showFieldError(e.field, e.message)
    );
  } else {
    showGenericError();   // 古い API or 未知の形式
  }
}
```

逆に、Content-Type を見ずにステータスだけで分岐すると:
- nginx が返した HTML エラーページを `res.json()` しようとしてクラッシュ
- 旧 API の Map 形式と新 API の ProblemDetail を取り違えて `errors` の forEach がコケる

といった事故が起きる。

### 2.5 なぜ「エラー専用」なのか

`application/problem+json` ラベルは **エラー専用**。理由はラベル名ではなく **中身のスキーマの都合**:

ProblemDetail の標準 5 フィールド（`type` / `title` / `status` / `detail` / `instance`）は全部「エラーを説明するため」のもの。成功時の `200 OK` で User を返すレスポンスに `title: "Validation Failed"` を入れる意味がない。

成功レスポンスは普通の `application/json` のまま、各 API が自分のスキーマで自由に返す。理由は単純で、成功レスポンスはエンドポイントごとに形が違いすぎて（User / Workspace / Channel...）、共通スキーマを作りにくいから。

→ **エラーは共通項が明確 → 標準化された / 成功は共通項が不明確 → 標準化が広まらない**。これが結果の世界観。

### 持ち帰る 1 行

> 「エラー判定 = **ステータスコード**、ボディの形の宣言 = **Content-Type**。`application/problem+json` は ProblemDetail 形式の宣言で、これがあるからフロントは安心して `errors[]` を読める」

---

## 3. 「部分更新」という API 設計の選択肢

### 3.1 更新 API の 2 派

| 方針 | やること | HTTP メソッド |
| --- | --- | --- |
| **全置換** | リクエストの内容で既存リソースを丸ごと上書き。送られなかったフィールドは `null` / 初期値で上書き | PUT（本来の意味） |
| **部分更新（PATCH 的セマンティクス）** | リクエストに書かれたフィールドだけ更新、書かれてないフィールドは触らない | PATCH（または PATCH 的 PUT） |

F-02 では **部分更新** を採用した。E2E での挙動:

```bash
# displayName だけ送る
PUT /api/users/me { "displayName": "新しい名前" }
→ 200 { "displayName": "新しい名前", "statusMessage": "🎧 集中モード" }  ← status は不変

# 次に statusMessage だけ送る
PUT /api/users/me { "statusMessage": "実装中" }
→ 200 { "displayName": "新しい名前", "statusMessage": "実装中" }  ← displayName は不変
```

もし全置換だったら、最初のリクエストで「statusMessage が送られていないから空文字に上書き」となる。それを避けるにはフロントが毎回「現在の値を全部 GET してから、変更箇所だけ書き換えて PUT する」必要があり、面倒。

### 3.2 F-02 で部分更新を選んだ理由

D-4 画面設計で、プロフィール編集はモーダル（M-05）で行うことが決まっている。ユーザーは「**表示名だけ変えたい**」「**ステータスだけ変えたい**」という編集を頻繁にやる想定。だから 1 フィールドだけ送れば済む API が UX に合う。これが Slack 的な UI（編集が小刻みに行われる）の典型パターン。

逆に「フォーム全体を 1 回で全部送って一括登録」というような UI なら、全置換のほうが素直で実装も簡単。

### 3.3 判断基準（引き出し）

| 部分更新が向いている | 全置換が向いている |
| --- | --- |
| プロフィール編集（フィールド単位でいじる） | 新規作成 |
| インライン編集（1 フィールドずつ確定） | 全フィールド入力の長いフォーム |
| **Slack 的にユーザーが頻繁に細かく変更する設定** | バックアップからの「丸ごと復元」 |

### 3.4 F-03 以降の AI への指示例

> 「ワークスペースの設定変更 API（PUT /api/workspaces/{wsId}/settings）は部分更新で作って。送られなかったフィールドは触らない方針で」

→ AI は F-02 と同じパターン（**送られたフィールドだけ更新、null は無視**）を再現できる。user は **「このリソースは部分更新がいいか全置換がいいか」を判断する**だけでよい。実装の細かい部分（Bean Validation の null skip 挙動など）は AI 任せでよい領域。

### 持ち帰る 1 行

> 「更新 API は **全置換 vs 部分更新** の 2 派。**Slack 的な細かい編集 UI には部分更新が合う**。F-02 はこの判断で部分更新を採用した」

---

## 4. JPA の dirty checking と `@Transactional` のトランザクション境界

### 4.1 「保存処理が書かれていない」のに DB が更新される

PR #40 の UserService、改めて読むと **`save()` がどこにもない**:

```java
@Transactional
public UserResponse updateMe(Long userId, UpdateMeRequest req) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

    if (req.displayName() != null) {
        user.setDisplayName(req.displayName());     // ← セッターで値を変えてるだけ
    }
    if (req.statusMessage() != null) {
        user.setStatusMessage(req.statusMessage()); // ← セッターで値を変えてるだけ
    }

    return UserResponse.from(user);                 // ← save() なし
}
```

にもかかわらず E2E で実際に DB が更新された（GET /api/auth/me で反映確認済み）。仕掛けは **JPA の dirty checking**。

### 4.2 2 つの永続化スタイル

| スタイル | やること | 例 |
| --- | --- | --- |
| **明示的保存スタイル** | プログラマが「ここで保存して」と毎回宣言する | 生 SQL の `UPDATE ...`、NoSQL の `repo.save(obj)`、TypeORM の明示 save |
| **ORM の自動追跡スタイル**（JPA / Hibernate / Doctrine など） | **トランザクション範囲内でオブジェクトを変更すると、ORM が自動で UPDATE を発行** | JPA、Hibernate、Doctrine（PHP）など |

JPA では、`@Transactional` で囲まれたメソッドの中でエンティティのセッターを呼ぶだけで、ORM が「あ、このフィールドが変わったな」と検出し、トランザクション終了時に勝手に `UPDATE users SET display_name = ... WHERE id = ...` を発行する。これが **dirty checking**（汚れた = 変更された箇所を検出する）。

### 4.3 設計判断としての「トランザクション境界」

dirty checking が効くのは `@Transactional` の範囲内だけ。だから **「どこにトランザクションを引くか」**が重要な設計判断。

F-02 で採用した引き方:

```
Controller   ← トランザクションなし（HTTP の入口）
   ↓
Service      ← @Transactional ★ ここに境界を引く
   ↓
Repository   ← 境界の中で動く
```

→ **「1 つの Service メソッド = 1 つのトランザクション」**が JPA + Spring の王道。Service 層は「1 つのビジネス操作」を表現する単位なので、ここをトランザクション境界にするのが自然。

Controller に付けると HTTP リクエストの受付処理まで巻き込み広すぎる。Repository に付けるとメソッドごとに細切れになり、複数 Repository をまたぐ操作で整合性が取れない。

### 4.4 引き出し（AI への指示で使う）

| シーン | 引き出し |
| --- | --- |
| **JPA を使う Spring プロジェクト** | Service メソッドに `@Transactional`、中でエンティティを変更するだけで OK、`save()` は不要 |
| **NoSQL や生 SQL を使うプロジェクト** | 明示的な保存呼び出しが必要 |
| **AI が `userRepository.save(user)` を書いてきたとき** | 「JPA の dirty checking で不要なはずだけど、入れる理由ある？」と一度疑える |

### 持ち帰る 1 行

> 「JPA の **dirty checking** = `@Transactional` 範囲内でエンティティを変更すれば、ORM が自動で UPDATE を発行する。**Service 層に `@Transactional` を置く**のが Spring + JPA の王道パターン」

---

## 5. PR #40 の設計判断メモ（残しておく中間状態）

### 5.1 既存 AuthExceptionHandler を触らなかった理由

[backend/src/main/java/com/raisechat/auth/exception/AuthExceptionHandler.java](../../backend/src/main/java/com/raisechat/auth/exception/AuthExceptionHandler.java) は `Map<String, String>` を返す旧形式で、D-1 規約の RFC 7807 ProblemDetail に未準拠。さらに validation 失敗時に 400 を返している（D-1 規約は 422）。

PR #40 では新 advice [GlobalExceptionHandler](../../backend/src/main/java/com/raisechat/common/exception/GlobalExceptionHandler.java) を `basePackages = "com.raisechat.user"` + `@Order(HIGHEST_PRECEDENCE)` でスコープ限定し、auth 既存には手を入れない方針にした。

理由:
- **F-02 PR のスコープを最小化**したかった
- AuthControllerIT の期待値も書き換える必要が出ると、F-02 の本質から外れる作業が混ざる
- auth の規約準拠は別 Issue として全体的に移行する

→ 結果として **「auth 系は Map、user 系は ProblemDetail」の混在状態**が一時的に残っている。将来 AuthExceptionHandler を削除して GlobalExceptionHandler の `basePackages` / `@Order` を外せば、自然に全体 advice として機能する設計になっている。

→ **「あえて中間状態を残す」**スコープ判断は、PR 単位の学習効率と将来のリファクタしやすさを両立する典型例。

### 5.2 アバター API (POST /api/users/me/avatar) を切り離した理由

D-1 §5.2 では F-02 にアバター画像アップロード API も含まれているが、PR #40 ではあえて切り離した。

理由:
- アバター保存先は AWS S3 想定だが、**インフラ設計（docs/infrastructure.md）が未着手**
- LocalStack か AWS 接続かの判断も未決
- S3 セットアップに時間をかけると、F-02 の本質（プロフィール更新の REST 実装）が霞む

→ **依存関係が深いタスクは PR を分ける**判断。後続 Issue で S3 / LocalStack セットアップとセットで実装する予定。

---

## 6. 引き出しまとめ（F-03 以降で再利用するパターン）

| 引き出し | F-02 での適用 | F-03 以降での使い所 |
| --- | --- | --- |
| **ProblemDetail でエラー JSON を標準化** | validation 失敗を 422 + ProblemDetail で返す | 全 REST API のエラーレスポンス |
| **Content-Type で形式宣言** | `application/problem+json` を Spring が自動付与 | RaiseChat 全体で同じ仕組みが効く |
| **部分更新（PATCH 的セマンティクス）** | プロフィール編集 | ワークスペース設定、チャンネル設定、ユーザー設定など、細かく編集される系 |
| **Service 層に `@Transactional`、JPA dirty checking** | UserService.updateMe | ほぼ全ての書き込み Service メソッド |
| **既存に触らないスコープ判断** | auth 既存 advice を温存 | 段階的リファクタリングが必要な場面で再利用 |
| **依存が深い機能は PR を分ける** | アバター API を切り離し | 外部システム連携（S3、SES、Stripe など）を含む機能で再利用 |

---

## 7. 関連リンク

- 設計書: [docs/api-design.md §5.2](../api-design.md)（F-02 仕様）
- 設計書: [docs/api-design.md §2.2](../api-design.md)（エラーレスポンス共通仕様）
- 画面設計: [docs/screen-design.md §5.9 M-05](../screen-design.md)
- 機能要件: [docs/functional-requirements.md §2.68](../functional-requirements.md)
- 実装 PR: [PR #40](https://github.com/gitmrk2k6/RaiseChat/pull/40)
- 関連ノート: [api-design-fundamentals.md](api-design-fundamentals.md) / [jpa-entity-mapping.md](jpa-entity-mapping.md) / [auth-jwt.md](auth-jwt.md)
