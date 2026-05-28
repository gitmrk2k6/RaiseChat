# 学習ノート — F-03 ワークスペース API（PR #44 振り返り）

関連設計書: [docs/api-design.md](../api-design.md) §5.3
関連実装: [PR #44](https://github.com/gitmrk2k6/RaiseChat/pull/44)
関連ノート: [docs/learning/f02-profile-api.md](f02-profile-api.md) / [docs/learning/api-design-fundamentals.md](api-design-fundamentals.md) / [docs/learning/jpa-entity-mapping.md](jpa-entity-mapping.md)

このノートは PR #44（F-03 ワークスペース API）のマージ後振り返り学習を、**設計判断とパターン引き出し**として残したもの。Spring / Java の細かい syntax は省き、**「次に AI に何を指示するか、その指示が正しいか判断できる力」**を養う観点で再構成している。**前提知識ゼロから読める**順序を意識した。

振り返り会話中に特に詰まった Step（advice scope のホワイトリスト方式、cursor ページング、DB トリガー × refresh、N+1 回避）を **重点深掘り**してある。

---

## 0. このノートの位置づけ

| ファイル | 目的 | 読む順 |
| --- | --- | --- |
| `docs/api-design.md` §5.3 | **決定事項**: F-03 ワークスペース API の仕様 | 仕様を確認したい時 |
| `docs/learning/f03-workspace-api.md` (本書) | **なぜそう決めたか / 引き出し**: 設計判断の根拠と再利用可能なパターン | 次に同種の API を作るとき、AI に指示するとき |

---

## 1. advice scope のホワイトリスト方式 — エラー JSON 形式が混在しているプロジェクトでの捌き方

### 1.1 まず用語を整理する

「**advice**」「**scope**」「**競合**」のような Spring 固有の言葉が出てくるが、抽象化して理解する。

| 言葉 | 意味（抽象化版） |
| --- | --- |
| **advice**（アドバイス） | コントローラ（API のエンドポイント）から飛んできた例外を**横取り**して、HTTP レスポンスに変換するための仕組み |
| **scope**（スコープ） | その advice が「どの API の例外を担当するか」の担当範囲 |
| **競合** | 1 つの例外に対して複数の advice が「私が担当します」と手を挙げてしまう状態 |
| **@Order** | 競合した時の優先順位の指定。HIGHEST_PRECEDENCE = 最優先、何も指定しないと最後 |

イメージとしては「**Slack のチャンネル通知ルール**」に近い。「@channel に反応するチャンネル」を限定したいので、ルールごとに対象チャンネルを指定する。Spring の advice もそれと同じで、**対象パッケージ**を指定して担当範囲を切る。

### 1.2 RaiseChat に存在する 2 つの advice

PR #44 時点でプロジェクトには 2 つの advice が共存している:

| advice | 形式 | 担当範囲（scope） |
| --- | --- | --- |
| `AuthExceptionHandler` | **旧仕様**（Map 形式の自由 JSON、validation は 400） | auth パッケージ |
| `GlobalExceptionHandler` | **新仕様**（RFC 7807 ProblemDetail、validation は 422） | F-02 時点では user パッケージのみ |

D-1 API 設計書では「エラーは全部 ProblemDetail で統一」と決めているが、auth は F-01 で実装した古いコードで未移行。**「auth を移行するのは別 Issue」**と判断して、F-02 では新 advice を `basePackages = "com.raisechat.user"` で囲い、互いに干渉しない状態を作っていた。

F-03 で workspace パッケージが新しく増える。**workspace も新仕様（ProblemDetail）で動かしたいが、auth は旧仕様のまま残したい**。

### 1.3 私が user に提示した最初の選択肢（後で問題が判明）

「advice の担当範囲を広げる」 = **scope を `com.raisechat` 全体に拡張する**、を提案した。一見シンプル。

しかしこれを実装すると、こうなる:

```
GlobalExceptionHandler (新仕様): 全体担当、最優先
AuthExceptionHandler  (旧仕様): 全体担当、優先度デフォルト
```

→ 両方が **auth の例外も**「私が担当します」と手を挙げる。優先度で GlobalExceptionHandler が勝つので、**auth でも 422 + ProblemDetail に変わる**。

これは AuthControllerIT（既存テスト）が 400 を期待しているので壊れる。さらに、`AuthExceptionHandler` の ProblemDetail 化は **別 Issue として残しておきたい**作業（F-02 で明示的に先送りした）。

### 1.4 ホワイトリスト方式という解決策

scope を「**ここ**」と限定列挙する方式に切り替えた:

```
GlobalExceptionHandler (新仕様): basePackages = { user, workspace } を明示的に列挙、最優先
AuthExceptionHandler  (旧仕様): 全体担当、優先度デフォルト
```

これだと、auth で起きた例外は GlobalExceptionHandler の担当範囲に含まれない → 手を挙げない → AuthExceptionHandler が拾う。**競合が物理的に発生しない**ので、優先度の話すら不要になる。

| 方式 | 動き | F-03 での適合 |
| --- | --- | --- |
| 全体に拡張 + 優先度で勝つ | 競合は起きるが、優先度で押し切る | auth テストが壊れる、副作用付き |
| **ホワイトリスト方式** | **そもそも競合させない** | **副作用ゼロ、F-03 のスコープに収まる** |

### 1.5 ホワイトリスト方式の代償（F-04 以降）

新しいパッケージが増えるたびに `basePackages` の配列に追加する必要がある。

例: F-04 で channel パッケージを追加する時 → `basePackages = { user, workspace, channel }` と書き換える。

「**忘れたらどうなる?**」 → 新パッケージの例外に対して新仕様 advice が反応しない → 旧仕様の AuthExceptionHandler が拾う → 旧形式 (Map / 400) で返ってしまう → テストが落ちて気づく仕掛け。

→ **テストが守ってくれる**設計なので、忘れても致命傷にはならない。

### 1.6 この判断の引き出し

| シーン | AI への指示 |
| --- | --- |
| F-04 で channel パッケージを追加 | 「GlobalExceptionHandler の basePackages に `com.raisechat.channel` を追加して」 |
| 既存システムに新仕様を共存させたい | 「新 advice はホワイトリスト方式で対象パッケージを限定して。旧 advice には触らない」 |
| 「scope を全体に拡張」案が出てきたら | 「他の advice と競合しないか、AuthControllerIT のような既存テストが壊れないか確認して」と一言追加 |

### 1.7 なぜ F-02 時点ではこの問題が見えなかったか

F-02 では新パッケージが 1 つ（user）だけだったので、`basePackages = "com.raisechat.user"` でも「1 パッケージだけホワイトリスト」と同じだった。**新パッケージが 2 つ目（workspace）になって初めて「列挙すべき配列」だと気づく構造**だった。

→ **「同種のパッケージが増えると、設計判断が浮かび上がる」**という典型例。F-04 以降は最初から「これは配列」と思って書ける。

### 持ち帰る 1 行

> 「複数の advice が共存するプロジェクトでは、**ホワイトリスト方式（basePackages で明示列挙）で競合を物理的に発生させない**のが安全。新仕様だけ対象パッケージを限定すれば、旧仕様は触らずに段階移行できる」

---

## 2. cursor ベースページング — リアルタイム性のあるデータ一覧の標準パターン

### 2.1 まず用語を整理する

| 言葉 | 意味 |
| --- | --- |
| **ページング** | 大量のデータを一気に返さず、小分けにして返す仕組み |
| **オフセット型** | 「11 件目から 20 件目まで」と**順番の数**で指定する伝統方式 |
| **cursor 型** | 「**この目印より後**の 20 件」と**目印**で指定する方式 |
| **limit** | 1 ページに返す件数の上限 |
| **hasMore** | 次のページがあるかどうかのフラグ |
| **nextCursor** | クライアントが次のページを取りに来る時に渡すべき「次の目印」 |

### 2.2 なぜ cursor 型を選んだか

D-1 API 設計書で「ページングは cursor ベース」と決めていた。理由は、オフセット型の**ズレ問題**:

オフセット型の弱点 = **データが追加されるとズレる**。たとえば「11〜20 件目」を取ってる間に新しいデータが先頭に追加されると、次の「21〜30 件目」を取った時に **前のページに含まれていた項目がもう一度出てくる**ことがある。

チャット、SNS、タイムラインのような「**常に新着が増える**」性質のデータでは致命的。RaiseChat はチャットアプリなので、メッセージ一覧、ワークスペース一覧、すべて新着が入る可能性がある → 全部 cursor 型で揃える。

### 2.3 cursor の中身に何を使うか

「目印」として何を使うかは設計判断の余地がある:

| 候補 | 性質 |
| --- | --- |
| **id（数字、auto-increment）** | 単純、必ずユニーク、ソートも安定 |
| **created_at（日時）** | 時系列順の意味を持つが、同じ秒に複数件あるとブレる |
| **(created_at, id) の複合** | 時系列順を厳密に表現できるが実装が複雑 |

F-03 では **id の単純な昇順** を採用した。ワークスペース一覧は件数が少ない（1 人で何十個も作らない）想定なので、シンプルで十分。

→ メッセージ一覧（F-05）では「時系列降順」が UX 上必要なので、**created_at + id の複合カーソル**にする判断が出てくる可能性が高い。F-03 はあくまで「シンプルなケース」として実装した。

### 2.4 「limit + 1 件取る」テクニック

ここが今回の振り返りで一番詰まったところ。順を追って整理する。

**クライアント** から「20 件ください」とリクエストが来た時、**サーバー** は **21 件** DB に問い合わせる。

| 返ってきた件数 | 意味 | レスポンスでの扱い |
| --- | --- | --- |
| 21 件 | 次のページがある | `hasMore=true`、**20 件目までを返す**（21 件目は捨てる） |
| 20 件以下 | 次のページはない | `hasMore=false`、全部返す |

**なぜわざわざ 1 件多く取るのか?** 「次のページが存在するか」を**1 回の DB 問い合わせ**で確実に知るため。

代替案として「件数を別途 COUNT クエリで数える」方法もある。だが COUNT は **2 回 DB に問い合わせ**になり、データ量が増えると重い。「1 件多く取る」なら 1 回で済む。

→ **オフセット型と cursor 型で共通して使える**汎用テクニック。F-04 / F-05 でも同じ手で書ける。

### 2.5 nextCursor の決め方

- `hasMore = true` なら、**返す 20 件のうち最後の項目の id** を nextCursor として返す
- `hasMore = false` なら、`nextCursor = null`（次がないことを明示）

クライアントは次のページが欲しい時、このまま `?cursor=...` に渡せば良い。**クライアントは cursor の中身が id だと意識する必要はない**（透過 cursor）。

→ サーバー側で「実は id 昇順だけど将来 created_at + id の複合に変えるかも」となっても、cursor の中身を変えれば対応できる。クライアントは触る必要がない。これが**抽象化されたインターフェース**の利点。

### 2.6 ソートキーと目印を揃える

設計上の地雷ポイント:

| ソート | 目印 | 動くか |
| --- | --- | --- |
| id 昇順 | id | ◯ |
| created_at 降順 | created_at | ◯ |
| created_at 降順 | id | × （ソートと目印がズレる → cursor が機能しない） |

→ **「ソートキー」と「目印に使う列」は同じものでないと cursor が機能しない**。F-03 は id 昇順 + 目印 id なので問題なし。

### 2.7 この判断の引き出し

| シーン | AI への指示 |
| --- | --- |
| F-04 でチャンネル一覧を実装 | 「F-03 と同じ cursor 型ページングで。id 昇順、limit+1 件取って hasMore 判定、nextCursor は最後の id」 |
| F-05 でメッセージ履歴を実装 | 「メッセージは時系列降順なので、cursor は created_at + id の複合で。limit+1 のテクニックは同じ」 |
| 「オフセット型でいいですか?」と AI に聞かれたら | 「常に新着が増えるリソースなので cursor 型で」と指示 |

### 持ち帰る 1 行

> 「**チャット / SNS のような新着が増えるデータは cursor 型ページング**。**limit+1 件取って hasMore を判定**するのが定番テクニック。**ソートキーと目印を一致させる**のが地雷回避ポイント」

---

## 3. DB トリガー設定カラムと entityManager.refresh() — 「DB が値を埋めるカラム」の取り扱い

### 3.1 何が起きたか

`POST /api/workspaces` の統合テストで、`createHappyPath()` が落ちた。レスポンスの `createdAt` フィールドが **null** で返ってきた。

仕様としては「サーバー側で created_at を自動セットして、レスポンスに含めて返す」だったので、ここが null だとフロントが「いつ作られたか」分からない。

### 3.2 なぜ null になったか

`Workspace` エンティティの `createdAt` カラムは、こう設計されている:

- DB 側で **トリガー**（INSERT/UPDATE 時に自動的に動く処理）が `now()` をセット
- Java 側（JPA エンティティ）からは「**書き込まない**」（`insertable = false`）

つまり「**DB が責任を持って値を埋めるカラム**」。Java は値を渡さないし、書き換えもしない。

**問題**: Java の Workspace オブジェクトを `new` して `save()` した直後、**Java オブジェクトの createdAt フィールドは null のまま**。DB には正しい値が入っているが、Java 側はまだそれを知らない。

`WorkspaceResponse.from(ws)` を呼ぶと、Java オブジェクトの null をそのままレスポンスに詰めてしまう。

### 3.3 最初試して失敗したアプローチ

「`save()` の後、もう一度 `findById()` すれば DB の値が読めるだろう」と考えた。

→ 通用しない。理由は **「永続コンテキスト」というキャッシュの存在**。

JPA は「同じトランザクション内で同じ id の操作をしたら、同じ Java オブジェクトを返す」というキャッシュを内部に持っている。これが**永続コンテキスト**。

`save()` した直後に同じトランザクション内で `findById(同じ id)` を呼ぶと、JPA は「あ、それさっき save したやつね」とキャッシュから返す → DB に SELECT を投げない → **Java オブジェクトの null のまま**。

### 3.4 正しい解決策: `entityManager.refresh()`

JPA に「**DB から強制的に読み直して、Java オブジェクトを上書きしてくれ**」と命令する。これが `refresh()`。

セットで使うのは:

1. **`saveAndFlush()`**: 「今すぐ DB に INSERT を投げる」（普通の `save()` はトランザクション終了時にまとめて投げる）
2. **`entityManager.refresh()`**: 「DB から最新の値を読み直して Java オブジェクトを更新」

この 2 つで「DB トリガーが埋めた値を Java 側に反映」できる。

### 3.5 代替手段: `@Generated(GenerationTime.INSERT)`

Hibernate のアノテーションで、エンティティの該当カラムに付けると「INSERT 後に自動で読み直す」処理を JPA が裏でやってくれる。

| 方式 | メリット | デメリット |
| --- | --- | --- |
| **Service 層で refresh()** | エンティティを触らない、影響範囲が局所的 | Service ごとに refresh を書く必要 |
| **エンティティに @Generated** | 一度書けば全 Service で効く | エンティティ変更が他の機能に波及するリスク |

F-03 では **Service 層で refresh()** を選んだ。理由は「**エンティティはすでに F-02 で誰かが書いた共通部品**で、ここを変えると他にも影響するかもしれないから」。

→ **「影響範囲が小さい方を選ぶ」**は設計判断の基本ルール。

### 3.6 この判断の引き出し

| シーン | AI への指示 |
| --- | --- |
| エンティティに `insertable = false` のカラムがある + INSERT 後にレスポンスに含めたい | 「Service の save 後に `saveAndFlush + entityManager.refresh()` で読み戻して」 |
| エンティティの設計を触っていいなら | 「`createdAt` に `@Generated(GenerationTime.INSERT)` を付けて」 |
| 「`save()` の後 `findById()` で取り直せばいい?」と AI が提案してきたら | 「JPA の永続コンテキストが効くので findById は同じインスタンスを返す、それじゃ反映されない」と指摘 |

### 3.7 「テストで初めて気づく」設計

実装中はこのバグに気づけなかった。なぜなら、ロジックを書いてる時点では「DB トリガーで created_at が設定される」と知っているので、**レスポンスに含まれると思い込んでいた**。

テストが「null が返ってきた」と教えてくれて初めて気づいた。

→ **設計判断としての教訓**: DB 側に値を任せるカラム（auto-increment、DEFAULT、トリガー）は、**Java から触る経路で「いつどう読まれるか」を意識しないと罠になる**。テストで早期発見する仕組みが命綱。

### 持ち帰る 1 行

> 「**DB トリガーで埋めるカラム**を `insertable=false` で宣言したら、**INSERT 後に Java 側を更新するための `entityManager.refresh()` がワンセット**。永続コンテキストのキャッシュで `findById()` は機能しないので注意」

---

## 4. JOIN FETCH と N+1 回避 — リストに関連データを含めるときの定番嗅覚

### 4.1 まず N+1 問題とは何か

「**一覧を取った後、各項目の関連データを 1 件ずつ取りに行くせいで、DB への問い合わせ回数が膨れ上がる現象**」。

F-03 の例で言うと: `GET /api/workspaces/{wsId}` のレスポンスに `members[]` を含めたい。members の各要素には「ユーザーの displayName / avatarUrl」が必要。

何も考えずに書くと:

1. メンバー一覧を取得（クエリ 1 回）
2. メンバー 1 人目の User 情報を取得（クエリ 1 回）
3. メンバー 2 人目の User 情報を取得（クエリ 1 回）
4. ...（メンバー人数分）

メンバー 5 人なら **1 + 5 = 6 回** の DB 問い合わせ。これが N+1（1 回 + N 回）。

### 4.2 JOIN FETCH での回避

「**関連データも一緒に取って**」と DB に一発で指示する書き方。1 回の問い合わせで members とその関連 User を同時に取れる。

→ クエリ回数: **1 回固定**。メンバーが 5 人でも 100 人でも 1 回。

### 4.3 設計時に気づく嗅覚

実装してから「あれ、遅い」と気づくのではなく、**設計時にレスポンスの構造を見て先回りで対処する**のが理想。

判定基準: **「リストの各項目に関連データを含めて返す」設計だったら、N+1 を疑う**。

| 設計 | N+1 リスク |
| --- | --- |
| GET /api/workspaces/{wsId}  → members[] を含む | あり（メンバーごとに User を取る） |
| GET /api/messages → 各メッセージに投稿者情報を含む | あり |
| GET /api/channels → チャンネル名と説明だけ返す | なし（関連データ不要） |

### 4.4 関連データを返さない時は JOIN FETCH 不要

逆に言うと、関連データを使わない API では JOIN FETCH を入れない。**無駄な JOIN は別の意味で重くなる**。

たとえば「ワークスペース一覧（GET /api/workspaces）」では `name / description / ownerUserId` だけ返す。owner の displayName まで返さないなら User を JOIN する必要がない → JOIN FETCH なし。

→ **「関連データを返すかどうか」で判断する**シンプルなルール。

### 4.5 この判断の引き出し

| シーン | AI への指示 |
| --- | --- |
| 「リストに関連データを含む」設計の API を作る | 「N+1 を避けるため JOIN FETCH で取って」 |
| 関連データを返さない API | JOIN FETCH 指示不要 |
| AI が普通の findAll を提案してきたら | 「レスポンスに関連 User を含むので、JOIN FETCH に直して」 |

### 持ち帰る 1 行

> 「**リストの各項目に関連データを含めるなら N+1 を疑う**。**JOIN FETCH で 1 回の問い合わせにまとめる**のが定番。関連データを返さない一覧では JOIN FETCH を入れないのが正解」

---

## 5. PR #44 の設計判断メモ（残しておく中間状態）

### 5.1 F-03 スコープを workspace + member に絞り、`general` 自動作成を F-04 に回した判断

D-1 API 設計書では「POST /api/workspaces で `general` チャンネルを自動作成する」と書かれている。F-03 で素直に実装すると、**まだ着手していない F-04（チャンネル API）で扱うべき channels テーブルへの低レベル INSERT が、F-03 に紛れ込む**。

選択肢:
- A: 設計書通り、F-03 で `general` 自動作成も実装
- B: F-03 のスコープを workspace + member に限定、`general` 自動作成は F-04 で実装

→ **B を採用**。理由:
- 1 タスク 1 スコープが学習効率に直結する
- F-04 のチャンネル CRUD が完成してから、その上に「workspace 作成時に呼ばれる」処理を足すほうが依存方向が素直
- 設計書は最終形を描くだけで、実装フェーズで切り出して進めて構わない

**残課題**: F-04 で「workspace 作成時に general を自動作成する」処理を WorkspaceService に追加する必要がある。F-04 の Issue に明示的に含める。

→ **「設計書通り全部やる」と「スコープを絞って段階的に進める」は別物**。設計書は user が書いたもの、スコープも user が決めて良い。

### 5.2 メンバー判定を Service 層手動チェックにした判断（@PreAuthorize 不使用）

`GET /api/workspaces/{wsId}` で「メンバーのみ閲覧可」のルールを実装する場所として、2 つの選択肢があった:

| 方式 | 場所 | 性質 |
| --- | --- | --- |
| **`@PreAuthorize` SpEL** | コントローラのアノテーション | 宣言的、Spring Security の機能 |
| **Service 層手動チェック** | Service メソッド内で if 文 | 普通の Java コード、テストしやすい |

F-02 が `@PreAuthorize` を使っていなかったので、**一貫性優先で Service 層手動チェック**を採用した。

→ Spring Security の SpEL は強力だが、学習者にとって「もう 1 つの言語を覚える」コストが高い。RaiseChat のような小〜中規模では Service 層チェックで十分。**「強力だが特殊な仕組み」より「素直な Java コード」を優先する**判断。

### 5.3 GlobalExceptionHandler の `basePackages` ホワイトリスト化（F-02 から繰り越した auth 旧仕様の扱い）

セクション 1 で詳述。要約:

- F-02 では `basePackages = "com.raisechat.user"` で新仕様 advice を限定していた
- F-03 で workspace パッケージが加わり、**配列形式のホワイトリスト**に拡張
- auth の `AuthExceptionHandler` は無変更で旧仕様のまま動く

**残課題**: 将来 `AuthExceptionHandler` を ProblemDetail 化する Issue がまだ残っている（F-02 から繰り越し）。それが完了すれば GlobalExceptionHandler の `basePackages` 制約を外せて、全体 advice として動かせる。

---

## 6. 引き出しまとめ（F-04 以降で再利用するパターン）

| 引き出し | F-03 での適用 | F-04 / F-05 以降での使い所 |
| --- | --- | --- |
| **GlobalExceptionHandler のホワイトリスト方式** | basePackages に workspace を追加 | F-04 で channel、F-05 で message を追加するたびに同様に拡張 |
| **cursor 型ページング（id 昇順 + limit+1）** | ワークスペース一覧 | チャンネル一覧、メンバー一覧、招待一覧など、件数が小さい一覧で再利用 |
| **複合 cursor（created_at + id）** | F-03 では使わず（今後の引き出し） | メッセージ履歴のような時系列降順が必要な一覧で必要になる |
| **DB トリガーカラム × `entityManager.refresh()`** | created_at をレスポンスに含める | INSERT 直後の auto-set 値を返す API 全般 |
| **JOIN FETCH で N+1 回避** | members[] の User を一括取得 | リストに関連情報を含めるすべての API |
| **Service 層手動チェックで認可判定** | メンバーのみ閲覧可 | チャンネルメンバーのみ書き込み可、ワークスペースオーナーのみ削除可、など |
| **1 タスク 1 スコープ判断** | `general` 自動作成を F-04 に回す | 設計書に複合的な振る舞いが書かれていても、実装は段階的に切り出す |

---

## 7. 関連リンク

- 設計書: [docs/api-design.md §5.3](../api-design.md)（F-03 仕様）
- 設計書: [docs/api-design.md §2.2](../api-design.md)（エラーレスポンス共通仕様）
- DB 設計: [docs/database-design.md](../database-design.md)（workspaces / workspace_members テーブル）
- 機能要件: [docs/functional-requirements.md](../functional-requirements.md)
- 実装 PR: [PR #44](https://github.com/gitmrk2k6/RaiseChat/pull/44)
- 関連ノート: [f02-profile-api.md](f02-profile-api.md) / [api-design-fundamentals.md](api-design-fundamentals.md) / [jpa-entity-mapping.md](jpa-entity-mapping.md)
