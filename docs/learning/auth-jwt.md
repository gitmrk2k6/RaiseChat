# 認証 API（Spring Security + JWT）学習ノート（チートシート）

PR #16 (C) で実装した「F-01 ユーザー認証 API」について、**何をしたか / なぜそうしたか / 困った時にどこを見ればいいか** を後から戻ってこれる形にまとめたもの。

---

## 0. 全体像（30 秒で復習）

```text
これまで:
ブラウザ ─────────→ Spring Boot ───→ PostgreSQL
            誰でも叩ける
            （守るものが何もない）

C 実装後:
ブラウザ ──login──→ Spring Boot
        ←─tokens─                  ① まず「鍵」をもらう
                                     (access + refresh の 2 種類)

ブラウザ ──API + Authorization: Bearer <access>──→ Spring Boot
                                                    ↓
                                              鍵を検証 OK
                                                    ↓
                                              「あなたは user.id=3」と
                                              SecurityContext に登録
                                                    ↓
                                              Controller がそれを参照
                                                    ↓
                                            ←── レスポンス
```

主役は **Spring Security**（門番）と **JJWT**（鍵の発行・検証）。

---

## 1. 用語の定義（迷ったら戻る）

| 用語 | 一言 |
| --- | --- |
| **Spring Security** | リクエストごとに「この人通していい？」を判定する Spring 標準の門番ライブラリ |
| **JWT (JSON Web Token)** | 「この人は user.id=3 です」と署名付きで保証する短命の名札。3 つの文字列を `.` で繋いだ形式 |
| **Access Token** | API を叩く時に毎回送る短命トークン（C では 15 分） |
| **Refresh Token** | Access が切れた時に新しい Access をもらうための長命トークン（C では 7 日） |
| **bcrypt** | パスワードを安全に保存するためのハッシュ関数。同じ入力でも毎回違う salt が混ざるので hash 値も毎回違うが、`matches()` で照合できる |
| **JJWT** | Java で JWT を扱う代表的なライブラリ（`io.jsonwebtoken:jjwt-*`） |
| **回転（rotation）** | refresh を使うたびに旧トークンを無効化＋新トークンを発行する設計 |

---

## 2. 追加した 4 エンドポイント

| Method | Path | Auth 要否 | 入力 | 出力 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup` | 不要 | userId, displayName, password | access + refresh |
| POST | `/api/auth/login` | 不要 | userId, password | access + refresh |
| POST | `/api/auth/refresh` | 不要(refresh tokenを送る) | refreshToken | 新 access + 新 refresh |
| GET | `/api/auth/me` | 必要(Bearer access) | ─ | 現在のユーザ情報 |

`/signup` した瞬間にトークンが返るので「登録 → 即ログイン状態」になる。

---

## 3. JWT の中身 — ただの「3 つの文字列を `.` で繋いだもの」

`eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIn0.signature` を `.` で 3 分割すると:

```text
┌─────────────┬─────────────────────┬──────────────┐
│  Header     │  Payload (Claims)    │  Signature   │
│             │                      │              │
│ {           │ {                    │ HMAC-SHA256( │
│  "alg":     │   "sub": "3",       │   header     │
│   "HS256"   │   "userId":"keisuke",│   + payload, │
│ }           │   "iat": 1716851234,│   secret     │
│             │   "exp": 1716852134 │ )            │
│             │ }                    │              │
└─────────────┴─────────────────────┴──────────────┘
   ↑ アルゴリズム  ↑ Base64 でエンコードされてるだけ  ↑ 改ざん検知用
                  （暗号化ではない！）
```

### ここが超大事

- **Payload は暗号化されていない**。Base64 デコードすれば誰でも読める
- **機密情報を入れてはいけない**（パスワードなど絶対 NG）
- 守られているのは **改ざん**。signature が secret 鍵で計算されているので、payload を書き換えると署名が合わなくなる

> 比喩: JWT は「印鑑付きの公開メモ」。誰でも読めるけど、印鑑がないと偽造できない。

---

## 4. なぜ DB を引かずに認証できるか

```text
【セッション方式（昔ながら）】
ブラウザ ─session_id="abc"→ サーバ
                              ↓
                          DB で「abc は誰？」を毎回引く

【JWT 方式（C の設計）】
ブラウザ ─Bearer eyJ...→ サーバ
                          ↓
                      署名検証 + Base64 デコード
                          ↓
                      payload に書いてある "sub":"3" を直接読む
                          ↓
                      user.id = 3 （DB アクセス 0 回）
```

- **メリット**: スケールしやすい。認証だけのために DB を引かないので速い・サーバを増やしやすい
- **デメリット**: **発行済み JWT を「無効化」する手段がない**。だから access は 15 分で切る（漏れても 15 分後には自然死）

`JwtAuthenticationFilter` が毎リクエストやっているのはこれだけ → 署名検証 → claims から user.id を取って `SecurityContext` に置く。

---

## 5. Refresh Token を DB に「ハッシュで」保存する理由

```text
                ユーザに返す値          DB に保存される値
                ─────────────         ──────────────────
生のトークン:    "abc123xyz..."          ─（持たない）
DB 保存:        ─                     SHA-256("abc123xyz...") = "f4a8c2d9..."

リフレッシュ時:
  ① ユーザが "abc123xyz..." を送ってくる
  ② サーバが SHA-256 を計算
  ③ DB から token_hash で検索 → 一致なら OK
```

### なぜハッシュで保存するか

| DB が漏れた場合 | 被害 |
| --- | --- |
| 生の refresh を保存していた | 全ユーザになりすまし可能 😱 |
| ハッシュで保存していた（C の設計） | ハッシュから元のトークンは復元不能 → なりすまし不可 ✅ |

これは **password_hash を bcrypt で保存するのと同じ思想**。「サーバが知らなくていい情報は持たない」。

### bcrypt じゃなく SHA-256 でいい理由

refresh token は元々ランダムな 32 バイトで「弱いパスワード」みたいな心配がない（辞書攻撃が効かない）。だから高速な SHA-256 で十分。

---

## 6. 回転（rotation）が何を守るか

`/refresh` を呼ぶたびに **旧 refresh は revoke + 新 refresh を発行**。

```text
時刻 t=0:   ログイン      → refresh_A 発行（DB: A=active）
時刻 t=15:  /refresh(A)  → A を revoke + refresh_B 発行
                            (DB: A=revoked, B=active)
時刻 t=30:  /refresh(B)  → B を revoke + refresh_C 発行
```

### refresh_A が漏れたシナリオ

| 設計 | 攻撃者 | 正規ユーザ |
| --- | --- | --- |
| 回転なし | `/refresh(A)` → 新 access ゲット 😱 | `/refresh(A)` → 新 access ゲット（どちらも気付かない） |
| 回転あり（C の設計） | `/refresh(A)` → 新 access ゲット | `/refresh(A)` → **401**「あれ、ログイン切れた？」→ 異常検知の手がかり |

回転は **「漏れたことを検知する仕組み」** として効く。被害を完全に防ぐわけではないが、片方が必ず詰まる。

---

## 7. 仕組みが守っているもの早見表

| 仕組み | 守っているもの |
| --- | --- |
| JWT 署名 (HS256) | リクエストの改ざん |
| JWT 短命 (15 分) | トークン漏洩時の被害時間 |
| Refresh token を DB に hash 保存 | DB 漏洩時の全員なりすまし |
| Refresh token 回転 | refresh 漏洩の検知 |
| bcrypt(password) | DB 漏洩時のパスワード復元 |
| login 失敗メッセージを統一 | 「この userId は存在する」の漏洩 |

---

## 8. 今回作ったファイル一覧（`com.raisechat.auth` パッケージ）

| ファイル | 役割 |
| --- | --- |
| `AuthController.java` | `/api/auth/*` の入口。リクエストを受けて Service に渡すだけ |
| `AuthService.java` | 認証ロジック本体（signup / login / refresh / me） |
| `RefreshToken.java` | `refresh_tokens` テーブルの JPA Entity |
| `RefreshTokenRepository.java` | `findByTokenHash` を持つ Spring Data Repository |
| `config/SecurityConfig.java` | SecurityFilterChain と PasswordEncoder Bean |
| `config/JwtProperties.java` | `application.yml` の `jwt:` セクションを `@ConfigurationProperties` で受ける record |
| `jwt/JwtService.java` | JWT の発行・検証・hash 化 |
| `jwt/JwtAuthenticationFilter.java` | 毎リクエストで Bearer を見て `SecurityContext` に principal を入れる |
| `jwt/AuthenticatedUser.java` | principal の中身（id, userId）を持つ record |
| `dto/{Signup,Login,Refresh}Request.java` | リクエストボディ |
| `dto/{Token,Me}Response.java` | レスポンスボディ |
| `exception/*.java` | カスタム例外と `@RestControllerAdvice` |

### 既存ファイルへの変更

| ファイル | 変更内容 |
| --- | --- |
| `backend/build.gradle` | `spring-boot-starter-security`, `jjwt-{api,impl,jackson}:0.12.6`, `spring-security-test` を追加 |
| `backend/src/main/resources/application.yml` | `jwt:` セクション追加（secret / access-ttl / refresh-ttl） |
| `backend/src/main/resources/db/seed/R__seed_dev.sql` | `password_hash` を実際に `"password"` を検証できる bcrypt ハッシュに修正 |

---

## 9. ハマりポイント / よくある勘違い

### ① seed の bcrypt ハッシュがダミーだった（PR #16 で修正）

C 実装中に発覚。旧値 `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` はネット上で例示として流布しているハッシュで、実は平文 `"password"` を検証してくれない。**「ハッシュ文字列をコピペで使う時は必ず自分の環境で `matches()` 検証する」**。

### ② JWT secret は 32 バイト以上必要（HS256 の場合）

短いと JJWT が `WeakKeyException` を投げる。`application.yml` の dev デフォルトは余裕を見て長めにしてある（`dev-secret-change-me-this-is-at-least-32-bytes-long`）。

### ③ Payload に機密情報を入れない

繰り返し: JWT の Payload は暗号化されていない。`sub`（user.id）や `userId` のような **「公開してもいい識別子」だけ**。メールアドレスや権限詳細を生で入れるのは避ける。

### ④ Filter 内で例外を投げない

`JwtAuthenticationFilter` は不正トークンを受けても **何もせず通過させる**。理由は「通過させた先で `authenticated()` 要求があれば自動的に 401 が返る」から。Filter 内で例外を投げると 500 になりがち。

### ⑤ `@Transactional` テストでも IDENTITY シーケンスはリセットされない

PostgreSQL の `GENERATED ALWAYS AS IDENTITY` は、テストでロールバックしても採番カウンタは進む。signup テストの userId は重複しないようにすればよい（id の絶対値に依存しないテストを書く）。

### ⑥ Spring Security 入れた瞬間、全部 401 になる

`SecurityConfig` で `permitAll` を書き忘れると、`/api/auth/login` すら 401。設定の順番は **「permit する path を先に列挙 → `anyRequest().authenticated()`」** が定型。

---

## 10. 動作確認コマンド集

```bash
# 起動
docker compose up -d
cd backend && ./gradlew bootRun

# 1. seed ユーザでログイン
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"keisuke","password":"password"}'
# → { "accessToken":"...", "refreshToken":"...", "expiresIn":900 }

# 2. me（access token を使う）
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <accessToken>"

# 3. refresh
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

# 4. signup
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"userId":"newuser","displayName":"New User","password":"password123"}'

# 回転の動作確認: 2 回目の refresh は失敗するはず
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<最初の refreshToken>"}'
# → 401（既に revoke 済み）
```

---

## 11. 関連ノート

- [backend-bootstrap.md](./backend-bootstrap.md) … docker-compose / Spring Boot 起動 / Flyway の基本
- [jpa-entity-mapping.md](./jpa-entity-mapping.md) … JPA Entity / Repository（`RefreshToken` も同じ流儀で書いている）
- [flyway-seed.md](./flyway-seed.md) … シードデータの仕組み（`password_hash` ダミーの背景もここ）
