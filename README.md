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

## MVP workflow

The Java backend owns profiles, vacancies, test assignments, answers, results,
rankings, and invitations. It calls the four JSON endpoints described in
`docs/python-contract-v0.2.md`. Python does not connect to PostgreSQL.

Set `PYTHON_BASE_URL` in `.env`. The default points to port 8000 on the Docker
host for local development. When the Python service joins this Compose project,
use `PYTHON_BASE_URL=http://python:8000`. The backend starts without Python, but
vacancy processing records a failed status until its calculations can be retried.

Select `CANDIDATE` or `EMPLOYER` with `PUT /api/v1/me/role` first. All routes
below require the same `X-Max-Init-Data` header as `/api/v1/me`.

| Role | Method | Path | Purpose |
| --- | --- | --- | --- |
| Candidate | `PUT`, `GET` | `/api/v1/candidate/profile` | Save or view profile |
| Candidate | `GET` | `/api/v1/candidate/test-assignments` | Assigned tests |
| Candidate | `GET` | `/api/v1/candidate/test-assignments/{id}` | Questions without answer keys |
| Candidate | `POST` | `/api/v1/candidate/test-assignments/{id}/answers` | Submit all answers once |
| Candidate | `GET` | `/api/v1/candidate/invitations` | Invitations |
| Candidate | `PUT` | `/api/v1/candidate/invitations/{id}/decision` | `ACCEPTED` or `DECLINED` |
| Employer | `POST`, `GET` | `/api/v1/employer/vacancies` | Create or list vacancies |
| Employer | `GET` | `/api/v1/employer/vacancies/{id}` | Vacancy and processing status |
| Employer | `GET` | `/api/v1/employer/vacancies/{id}/matches` | Matching explanations |
| Employer | `GET` | `/api/v1/employer/vacancies/{id}/test` | Test questions and answer keys |
| Employer | `GET` | `/api/v1/employer/vacancies/{id}/ranking` | Final and waiting candidates |
| Employer | `POST` | `/api/v1/employer/vacancies/{id}/assignments` | Send test to `{ "candidateId": "..." }` |
| Employer | `GET` | `/api/v1/employer/vacancies/{id}/results` | Assessment results |
| Employer | `POST` | `/api/v1/employer/vacancies/{id}/invitations` | Invite final candidate |

Vacancy creation accepts `testMode` `NONE`, `AUTO`, or `CUSTOM`. For `CUSTOM`,
include `customQuestions` with `type`, `competency`, `text`, `maxScore`, and
either `options` plus `correctAnswer.optionIndex`, or `rubric`. The defaults for
a test are seven minutes, seven AUTO questions, and level `JUNIOR`; AUTO
competencies default to the vacancy skill codes and weights. Matching runs at
creation; the employer can call `POST /{id}/matching/recalculate` beneath the
vacancy route after new candidates join. Failed AUTO generation can be retried
with `POST /{id}/test/generate/retry`; failed scoring with
`POST /{id}/assignments/{assignmentId}/score/retry`.

All salary values are monthly RUB and hour values are hours per week. Candidate
profiles need at least one skill and work format before matching. Python decides
eligibility; Java never sends answer keys or rubrics to the candidate API.

## Java package structure

The code is grouped by feature, then by responsibility:

```text
backend/
  auth/
    config/  exception/  filter/  model/  service/
  candidate/  assessment/  invitation/  matching/  python/  ranking/  vacancy/
  user/
    controller/  dto/  entity/  exception/  mapper/  repository/  service/
```

Add a package when the feature needs it. The `user` entity owns the rule that
a selected role cannot be changed; the service coordinates the transaction
and repository access.
