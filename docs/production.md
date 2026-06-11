# Production Deployment

This guide describes the production target for the current implementation.

## Build

```powershell
.\.gradle-local\gradle-8.14\bin\gradle.bat build :auth-service:standaloneJar
```

The auth-service standalone artifact is:

```text
auth-service/build/libs/geyser-edu-auth-service-0.2.0-SNAPSHOT-standalone.jar
```

It can be started directly:

```powershell
java -jar auth-service\build\libs\geyser-edu-auth-service-0.2.0-SNAPSHOT-standalone.jar
```

## Entra ID

Create an app registration:

- Platform: Web
- Redirect URI: `https://<AUTH_DOMAIN>/callback`
- Supported account type: your organization policy
- Client secret: store only in `.env` or a secret manager

Set `GEYSER_EDU_ALLOWED_TENANTS` explicitly in production.

## Deploy

```powershell
Copy-Item deploy\.env.example deploy\.env
```

Edit `deploy/.env`, then run from the `deploy` directory:

```powershell
docker compose up -d --build
```

Caddy terminates HTTPS and proxies to `auth-service`.

The Compose stack includes PostgreSQL. In production, `GEYSER_EDU_STORE_BACKEND=postgres` is required.

On Windows, you can also run the checked preflight script from the repository root:

```powershell
.\deploy\run-compose.ps1
```

It builds the project, starts Docker Compose, and waits for the auth-service Docker health check. The container health check calls `/health/ready`.

## Server Plugin Configuration

Use the same verifier token in Paper/Fabric:

```yaml
auth-service:
  enabled: true
  verify-url: "https://<AUTH_DOMAIN>/api/participation/verify"
  bearer-token: "replace-with-long-random-server-token"
  timeout-millis: 5000
```

## Health Checks

- `GET /health/live`
- `GET /health/ready`

`/health/ready` returns HTTP 503 until required production configuration is present.

## Admin API

Use `GEYSER_EDU_ADMIN_BEARER_TOKEN`.

```http
GET /api/admin/participation/list
Authorization: Bearer <admin-token>
```

```http
POST /api/admin/participation/revoke
Authorization: Bearer <admin-token>

{"participationId":"..."}
```

The revoke endpoint also accepts `{"idHash":"..."}` for tickets returned by the list endpoint.

## Operational Files

- Participation store: `GEYSER_EDU_TICKET_STORE`
- Audit log: `GEYSER_EDU_AUDIT_LOG`

When `GEYSER_EDU_STORE_BACKEND=postgres`, participation IDs are stored in PostgreSQL as HMAC-SHA256 hashes. `GEYSER_EDU_TICKET_STORE` is only used by the development `file` backend. Audit logs remain local files by default.

Do not rotate `GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET` while active participation IDs exist. Rotating it invalidates all unconsumed IDs.

## Remaining Production Hardening

- Add centralized log shipping and alerting.
- Run integration tests against a real Geyser/Floodgate server before rollout.
