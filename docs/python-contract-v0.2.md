# Java to Python contract v0.2

Source: `StaffMatch_Python_API_Contracts_v2.docx` supplied by the team. Python
is stateless and has no database access. The Java client sends JSON to these
endpoints and keeps input snapshots and version fields with results:

| Operation | Path | Request | Required response |
| --- | --- | --- | --- |
| Matching | `POST /api/v1/matching/calculate` | `vacancy`, `candidates[]` | `vacancyId`, `algorithmVersion`, `matches[]` with `candidateId`, `eligible`, `score`, `matched[]`, `partial[]`, `missing[]` |
| AUTO generation | `POST /api/v1/tests/generate` | `vacancyId`, `mode`, `role`, `level`, `durationMinutes`, `questionCount`, `competencies[]` | `testId`, `generationVersion`, `estimatedDurationMinutes`, `questions[]` |
| Scoring | `POST /api/v1/tests/score` | `assignmentId`, `questions[]`, `answers[]` | `assignmentId`, `scoringVersion`, `totalScore`, `questions[]`, `breakdown[]`; `summary` may be absent |
| Ranking | `POST /api/v1/ranking/calculate` | `vacancyId`, `weights`, `candidates[]` | `vacancyId`, `rankingVersion`, `final[]`, `waiting[]` |

Every request sends `X-Request-Id` and `Idempotency-Key` headers. These fields
are absent from the document's JSON examples, so their location is a Java-side
assumption. `status` per scored question, `strengths`, `risks`, `modelVersion`,
and ranking `explanation` are optional because the examples omit them. Generated
questions do not require `estimatedSeconds`; Java checks the returned total
duration of 5–10 minutes instead. Scores must be in 0–100. Python errors 400
and 422 are not retried; 429, 500, 503, and network failures receive at most
two retries. Real Python integration remains to be checked when that service is
available.
