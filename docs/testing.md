# Testing Quickstart

This guide is for trying the current build locally before production deployment.

## Build

```powershell
.\.gradle-local\gradle-8.14\bin\gradle.bat build :auth-service:standaloneJar
```

Artifacts:

- Paper plugin: `paper/build/libs/geyser-edu-gate-paper-0.2.0-SNAPSHOT.jar`
- Fabric mod: `fabric/build/libs/geyser-edu-gate-fabric-0.2.0-SNAPSHOT.jar`
- Auth service single jar: `auth-service/build/libs/geyser-edu-auth-service-0.2.0-SNAPSHOT-standalone.jar`

## Option A: Fast Local Test Without Entra ID

Use this first to confirm the Minecraft server gate works.

### Paper

1. Install Geyser/Floodgate on your Paper server.
2. Copy `paper/build/libs/geyser-edu-gate-paper-0.2.0-SNAPSHOT.jar` to the server `plugins` folder.
3. Start the server once, then edit `plugins/GeyserEduGate/config.yml`.
4. For a simple test, set:

```yaml
require-session-for-all-floodgate-players: true

local-session-issuer:
  enabled: true

auth-service:
  enabled: false
```

5. Restart the server.
6. In the server console or as OP:

```text
/edu-session issue test-tenant 10
```

7. Join from Bedrock/Education through Geyser.
8. When prompted, type the issued participation ID directly into chat.

The ID message is intercepted and should not be broadcast to other players.

### Fabric

1. Install Fabric server, Fabric API, Geyser/Floodgate if applicable.
2. Copy `fabric/build/libs/geyser-edu-gate-fabric-0.2.0-SNAPSHOT.jar` to the server `mods` folder.
3. Start once, then edit `config/geyser-edu-gate.properties`.
4. For a simple test, set:

```properties
require-session-for-all-floodgate-players=true
local-session-issuer.enabled=true
auth-service.enabled=false
```

5. Restart and run:

```text
/edu-session issue test-tenant 10
```

6. Join and enter the participation ID in chat.

## Option B: Entra ID End-to-End Test

Use this after the local server gate works.

### Entra ID App Registration

Create an app registration:

- Platform: Web
- Redirect URI: `http://127.0.0.1:8080/callback`
- Client secret: create one for local testing
- Supported account types: match your school tenant policy

Copy the application client ID, client secret, and tenant ID.

### Start Auth Service

```powershell
$env:GEYSER_EDU_ENV="development"
$env:GEYSER_EDU_AUTH_PORT="8080"
$env:GEYSER_EDU_CLIENT_ID="your-client-id"
$env:GEYSER_EDU_CLIENT_SECRET="your-client-secret"
$env:GEYSER_EDU_TENANT="organizations"
$env:GEYSER_EDU_REDIRECT_URI="http://127.0.0.1:8080/callback"
$env:GEYSER_EDU_ALLOWED_TENANTS="your-tenant-id"
$env:GEYSER_EDU_VERIFY_BEARER_TOKEN="shared-server-token"
$env:GEYSER_EDU_ADMIN_BEARER_TOKEN="admin-token"
$env:GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET="long-random-hash-secret-at-least-32-chars"
$env:GEYSER_EDU_STORE_BACKEND="file"
$env:GEYSER_EDU_TICKET_STORE="data/test-participation-tickets.tsv"
$env:GEYSER_EDU_AUDIT_LOG="logs/test-audit.tsv"
.\.gradle-local\gradle-8.14\bin\gradle.bat :auth-service:run
```

Check readiness:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8080/health/ready
```

Open:

```text
http://127.0.0.1:8080/login
```

After Microsoft login, the page displays a participation ID.

### Device Code Login

For the `microsoft.com/link` style flow, enable Paper device code mode:

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

When a gated player joins, the Paper plugin asks auth-service to start Microsoft device authorization. The player sees a URL and code in chat, signs in with the school account in a browser, and the plugin polls auth-service until the login is verified.

Your Entra app registration must allow device code/public client authentication. If Microsoft returns an error from `/devicecode` or `/token`, check the app registration's public client flow setting and tenant policy.

### Configure Paper/Fabric Verifier

Paper `plugins/GeyserEduGate/config.yml`:

```yaml
require-session-for-all-floodgate-players: true

local-session-issuer:
  enabled: false

auth-service:
  enabled: true
  verify-url: "http://127.0.0.1:8080/api/participation/verify"
  bearer-token: "shared-server-token"
  timeout-millis: 5000
```

Fabric `config/geyser-edu-gate.properties`:

```properties
require-session-for-all-floodgate-players=true
local-session-issuer.enabled=false
auth-service.enabled=true
auth-service.verify-url=http://127.0.0.1:8080/api/participation/verify
auth-service.bearer-token=shared-server-token
auth-service.timeout-millis=5000
auth-service.device-code.enabled=true
auth-service.device-code.start-url=http://127.0.0.1:8080/api/device/start
auth-service.device-code.poll-url=http://127.0.0.1:8080/api/device/poll
```

Restart the Minecraft server, join through Geyser, and enter the participation ID in chat.

On standard Fabric runtimes, device code mode shows the Microsoft login URL/code on join and polls auth-service automatically. On Minecraft Education 26.x runtimes, use `/edu-session login` to request a Microsoft login code through the reflective adapter while the full join/chat gate adapter is still being built.

## Admin Checks

List unconsumed participation IDs by hash:

```powershell
Invoke-WebRequest -UseBasicParsing `
  -Headers @{ Authorization = "Bearer admin-token" } `
  http://127.0.0.1:8080/api/admin/participation/list
```

Revoke by hash:

```powershell
Invoke-WebRequest -UseBasicParsing `
  -Method POST `
  -Headers @{ Authorization = "Bearer admin-token"; "Content-Type" = "application/json" } `
  -Body '{"idHash":"..."}' `
  http://127.0.0.1:8080/api/admin/participation/revoke
```

## Notes

- By default, only names matching `education-username-prefixes` are gated. For testing, `require-session-for-all-floodgate-players=true` is simpler.
- Participation IDs are one-time use.
- The auth service stores only HMAC-SHA256 hashes of participation IDs.
- For production deployment, use `docs/production.md`.
