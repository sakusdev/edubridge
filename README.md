# Minecraft Geyser for Education

Minecraft Education から Java Edition サーバーへ参加するための、Geyser 互換プロキシ構想です。

## 目的

- Minecraft Education 利用者が Java Edition サーバーへ参加できるようにする。
- Microsoft Entra ID の学校向けアカウントでログインしたユーザーだけを許可する。
- 同一テナントのユーザーに限定して、参加 ID を共有できるようにする。

## 基本フロー

1. ユーザーがプロキシのログイン画面を開く。
2. Microsoft Account / Entra ID の OAuth ログインへリダイレクトする。
3. 学校向けアカウントで認証し、テナント ID を検証する。
4. 許可テナントのユーザーであれば短寿命の参加 ID を発行する。
5. 参加 ID を同一テナントユーザーへ共有する。
6. Minecraft Education クライアントは、その参加 ID を使ってプロキシへ接続する。
7. プロキシが Java Edition サーバーとの通信を中継する。

## 重要な制約

- Microsoft のログイン画面は偽装せず、OAuth / OpenID Connect の公式フローを使う。
- 学校テナントの判定は ID トークンまたは Microsoft Graph で取得できるテナント情報に基づく。
- 参加 ID は認証済みユーザーの参加許可を表すものであり、Microsoft アカウントの資格情報やトークンを共有しない。
- 参加 ID には有効期限、発行テナント、発行ユーザー、参加先サーバーを紐づける。
- Java サーバー側へ接続する際の認証方式は、オンラインモード、Floodgate 相当のオフライン識別、または専用プラグイン連携のどれを採用するか決める必要がある。

## 想定コンポーネント

- `auth-service`: Microsoft Entra ID ログイン、テナント検証、参加 ID の作成、検証、失効、監査ログ。
- `bedrock-proxy`: Minecraft Education / Bedrock プロトコルの受け口。
- `java-bridge`: Java Edition サーバーへのプロトコル変換と接続管理。
- `admin-ui`: 許可テナント、接続先サーバー、セッションポリシーの管理。

## 現在の実装

Geyser/Floodgate と併用する `GeyserEduGate` を、Paper プラグインと Fabric サーバー mod の両方で提供します。

- Floodgate API が存在する場合は Floodgate プレイヤーを検出します。
- `require-session-for-all-floodgate-players` を有効にすると、すべての Floodgate プレイヤーにセッション参加を要求します。
- 既定では `education-username-prefixes` に一致するユーザーだけをゲート対象にします。
- 管理者は `/edu-session issue <tenantId> [ttlMinutes]` でローカル検証用の参加 ID を発行できます。
- プレイヤーは参加後の猶予時間内に、Entra ID ログイン後に発行された参加 ID をチャットへ入力します。
- セッション未確認の対象プレイヤーは kick されます。
- `common` モジュールに Education 互換ポリシー層を持ち、Paper/Fabric の両方から同じ判定を使います。
- `auth-service` モジュールで Microsoft Entra ID ログイン、参加 ID 発行、参加 ID 検証 API を提供します。

ローカル発行機能は開発用です。本番では `auth-service.enabled: true` にして、Entra ID ログイン後に発行された参加 ID を検証します。

## Auth Service

`auth-service` は Microsoft Entra ID でログインしたユーザーへ参加 ID を発行します。参加 ID は Minecraft Education クライアントのチャットへ入力し、Paper/Fabric 側が検証 API へ問い合わせます。

Paper では Microsoft device code flow も利用できます。この方式では参加 ID を手入力せず、サーバー参加時に表示されたコードを Microsoft の verification URL、通常は `https://microsoft.com/link`、で入力し、学校アカウントでログインします。auth-service が Microsoft の token endpoint をポーリングし、完了後にプレイヤーを許可します。

Fabric の通常 runtime でも同じ device code flow を使えます。Minecraft Education 26.x runtime では mapping 差異があるため、現時点では反射 adapter の `/edu-session login` でコード発行と完了確認を行います。

必要な Entra ID アプリ設定:

- Platform: Web
- Redirect URI: `http://127.0.0.1:8080/callback` または公開 URL の `/callback`
- Supported account types: 対象テナントに合わせる
- Client secret: 作成して `GEYSER_EDU_CLIENT_SECRET` に設定

起動例:

```powershell
$env:GEYSER_EDU_CLIENT_ID="your-client-id"
$env:GEYSER_EDU_CLIENT_SECRET="your-client-secret"
$env:GEYSER_EDU_TENANT="organizations"
$env:GEYSER_EDU_REDIRECT_URI="http://127.0.0.1:8080/callback"
$env:GEYSER_EDU_ALLOWED_TENANTS="tenant-id-1,tenant-id-2"
$env:GEYSER_EDU_VERIFY_BEARER_TOKEN="shared-server-token"
$env:GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET="long-random-hash-secret-at-least-32-chars"
$env:GEYSER_EDU_STORE_BACKEND="file"
$env:GEYSER_EDU_TICKET_STORE="data/participation-tickets.tsv"
$env:GEYSER_EDU_AUDIT_LOG="logs/audit.tsv"
$env:GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE="60"
.\.gradle-local\gradle-8.14\bin\gradle.bat :auth-service:run
```

ログイン URL:

```text
http://127.0.0.1:8080/login
```

Paper/Fabric 側の verifier 設定:

```yaml
auth-service:
  enabled: true
  verify-url: "http://127.0.0.1:8080/api/participation/verify"
  bearer-token: "shared-server-token"
  timeout-millis: 5000
  device-code:
    enabled: true
    start-url: "http://127.0.0.1:8080/api/device/start"
    poll-url: "http://127.0.0.1:8080/api/device/poll"
```

現在の `auth-service` は OAuth code exchange 後に Microsoft OpenID metadata / JWKS を取得し、ID token の RS256 署名、`aud`、`iss`、`tid`、`exp` を検証します。参加 ID は `GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET` で HMAC-SHA256 ハッシュ化して保存され、検証成功時に一回限りで消費されます。平文の参加 ID は発行画面に一度だけ表示されます。

運用 API:

```http
POST /api/participation/verify
Authorization: Bearer shared-server-token

{"participationId":"...","playerUuid":"...","playerName":"...","platform":"paper"}
```

```http
POST /api/admin/participation/revoke
Authorization: Bearer <admin-token>

{"participationId":"..."}
```

監査ログは既定で `logs/audit.tsv` に出力されます。verify API は `GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE` でリモートアドレスとプレイヤー UUID 単位の簡易レート制限を行います。

## Education 互換レイヤー

Minecraft Education は Bedrock 本体より遅れて複数の Bedrock 更新内容をまとめて取り込むことがあります。たとえば 2026 年 4 月の Education v1.21.133 は Bedrock 1.21.110 から 1.21.130 の変更を含む更新として扱われています。

このプロジェクトでは、ViaVersion のようにすべての Bedrock パケットを独自変換するのではなく、Geyser の変換処理を尊重しつつ、内蔵の Education 互換ポリシーで次を管理します。

- 既知の Education バージョン範囲。
- Education と見なすプレイヤーの判定条件。
- 未知または非対応プロトコルの扱い。
- セッション必須化の対象範囲。

現時点では Bukkit/Fabric のサーバー層から Education クライアントの正確な Bedrock protocol version を常に取れるわけではないため、`1.21.133` profile は permissive にしています。Geyser 側から正確な protocol metadata を取れるようになった段階で、`EducationCompatLayer` に downgrade/deny ルールを追加します。

## ビルド

Java 21 が必要です。

```powershell
.\.gradle-local\gradle-8.14\bin\gradle.bat build
```

成果物:

- Paper: `paper/build/libs/geyser-edu-gate-paper-0.2.0-SNAPSHOT.jar`
- Fabric: `fabric/build/libs/geyser-edu-gate-fabric-0.2.0-SNAPSHOT.jar`
- Auth service single jar: `auth-service/build/libs/geyser-edu-auth-service-0.2.0-SNAPSHOT-standalone.jar`

本番デプロイ手順は [docs/production.md](C:/Users/sakus/Documents/Minecraft-Geyser-for-edu/docs/production.md) にまとめています。
テスト利用手順は [docs/testing.md](C:/Users/sakus/Documents/Minecraft-Geyser-for-edu/docs/testing.md) にまとめています。

## 次に決めること

- Geyser/Floodgate から Education クライアントの protocol metadata を取得する方法。
- Java サーバーをオンラインモードで扱うか、Floodgate 互換の識別子で扱うか。
- 参加 ID の共有方法を URL、コード入力、管理画面配布のどれにするか。
- `auth-service` に production 向けの HTTPS 終端、管理 UI、外部DBストアを追加するか。
