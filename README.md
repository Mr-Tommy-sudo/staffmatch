# StaffMatch

## Java backend locally with Docker

Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` and the MAX bot token.
The bot token is used to verify MAX Web App `initData`; keep `.env` out of Git.
Set `CORS_ALLOWED_ORIGINS` to the exact origin hosting the frontend. Separate
multiple origins with commas; do not use `*` for a deployed environment.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The API is available at `http://localhost:8080`. The public health check is
`GET /actuator/health`. PostgreSQL data is kept in the `postgres_data` Docker
volume across container restarts.

Every `/api/v1/**` request must include the raw `window.WebApp.initData` value
in the `X-Max-Init-Data` header. The backend verifies the MAX signature and
accepts launch data issued within the last hour. The first authenticated
request creates a user without a role.

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| `GET` | `/api/v1/me` | — | `{ "id": "...", "role": null }` |
| `PUT` | `/api/v1/me/role` | `{ "role": "CANDIDATE" }` or `{ "role": "EMPLOYER" }` | Current user with the chosen role |

Choosing the existing role again succeeds. Choosing a different role returns
`409 Conflict`. Missing, invalid, or expired MAX launch data returns `401`.

The PostgreSQL schema is managed by Flyway. Add new changes as migrations; JPA
validates the schema at startup.
