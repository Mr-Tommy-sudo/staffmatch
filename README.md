# StaffMatch

Платформа подбора и оценки кандидатов. Работает как **MAX Web App**:
наниматель создаёт вакансии, находит кандидатов по совпадению навыков,
выдаёт им тесты и получает рейтинг; кандидат заполняет профиль, решает
тесты и отвечает на приглашения.

Система состоит из трёх частей:

| Часть | Папка | Назначение |
| --- | --- | --- |
| Мобильный веб-апп | [`frontend/`](frontend/README.md) | Интерфейс для MAX (вертикальные экраны). Без сборки. |
| Бекенд | `backend/` | Java 21 + Spring Boot + PostgreSQL. Хранит данные и ходит в Python. |
| Сервис оценки | `evaluation_service/` | Python (FastAPI). Матчинг, генерация тестов, проверка ответов, рейтинг. |

```
Кандидат / Наниматель ── MAX Web App (frontend/)
                                    │  X-Max-Init-Data
                          Java backend (localhost:8080)
                          ├── PostgreSQL (данные)
                          └── Python service (матчинг, тесты, рейтинг)
```

---

## 1. Подготовка

Требуются: **Docker + Docker Compose**, **Python 3.13 и uv** (для сервиса
оценки вне Docker), **Node.js не нужен** — фронтенд статика без сборки.

Скопируйте пример окружения в корне репозитория:

```bash
cp .env.example .env
```

Откройте `.env` и заполните обязательные переменные:

| Переменная | Обязательно | Что указать |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | да | Пароль БД (минимум для локальной разработки: что угодно, кроме `change-me` не обязательно; `change-me` подходит для разработки) |
| `MAX_BOT_TOKEN` | да | Токен MAX-бота. Используется для проверки подписи `initData`. НЕ коммитить. |
| `CORS_ALLOWED_ORIGINS` | да* | Точный origin фронтенда (см. шаг 4). Несколько — через запятую, `*` для продакшена нельзя. |
| `OPENROUTER_API_KEY` | нет | Для генерации AUTO-тестов и проверки свободных ответов. Матчинг, рейтинг и выбор вариантов работают без него. |
| `OPENROUTER_MODEL` | нет | Модель по умолчанию `openai/gpt-6-luna` |

`.env` исключён из Git (см. `.gitignore`) — секреты в коммиты не попадают.

---

## 2. Запуск бекенда и сервиса оценки (Docker)

Из корня репозитория:

```bash
docker compose up --build
```

Compose поднимает три сервиса: `postgres`, `python`, `backend` — и ждёт,
пока БД и Python станут здоровы. Логи смотреть так:

```bash
docker compose logs -f backend
```

После старта:

- API бекенда: `http://localhost:8080`
- Проверка здоровья: `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Python-сервис локально: `http://127.0.0.1:8000/api/v1/health` → `{"status":"ok"}`, OpenAPI: `http://127.0.0.1:8000/docs`

Данные PostgreSQL живут в Docker-томе `postgres_data` и переживают
перезапуск контейнеров. Сбросить БД полностью:

```bash
docker compose down -v
```

> Python доступен снаружи только на `127.0.0.1:8000`; внутри сети Compose
> бекенд обращается к `http://python:8000` (`PYTHON_BASE_URL` из `.env`).

### Запуск только Python-сервиса (без Docker)

Из папки `evaluation_service/`:

```bash
uv sync --frozen
uv run --env-file ../.env uvicorn app.main:app --host 127.0.0.1 --port 8000
```

---

## 3. Авторизация через MAX

Каждый запрос к `/api/v1/**` должен нести заголовок `X-Max-Init-Data` с
сырым значением `window.WebApp.initData`. Бекенд проверяет подпись по
`MAX_BOT_TOKEN` и принимает данные, выпущенные в течение последнего часа.

Первый запрос с валидной initData автоматически создаёт пользователя без
роли (регистрация не нужна). Затем роль выбирается единоразово:

| Метод | Путь | Тело | Результат |
| --- | --- | --- | --- |
| `GET` | `/api/v1/me` | — | `{ "id": "...", "role": null }` |
| `PUT` | `/api/v1/me/role` | `{ "role": "CANDIDATE" }` **или** `{ "role": "EMPLOYER" }` | Пользователь с выбранной ролью |

Смена роли невозможна — повторный выбор другой роли вернёт `409 Conflict`.
Пропущенная, невалидная или просроченная MAX-initData возвращает `401`.

---

## 4. Запуск фронтенда

Фронтенд — статика без сборки. Достаточно любого статик-хостинга
(nginx, GitHub Pages, S3, MAX CDN). Его origin обязан совпадать с
`CORS_ALLOWED_ORIGINS` в `.env` бекенда.

### Локально (для разработки)

```bash
cd frontend
python3 -m http.server 8000
```

Откройте `http://localhost:8000`. Если фронт на `:8000`, а бекенд на
`:8080`, укажите в `.env` бекенда `CORS_ALLOWED_ORIGINS=http://localhost:8000`
и перезапустите compose. В `config.js` базовый URL бекенда —
`http://localhost:8080`.

> Внутри MAX `window.WebApp.initData` подставляется автоматически. В обычном
> браузере откроется экран «Подключение»: вставьте реальную initData из MAX
> либо нажмите «Сгенерировать для теста» — укажите тот же `MAX_BOT_TOKEN`
> и произвольный `user id`; подпись считается тем же алгоритмом (HMAC-SHA256).

### Демо-режим без бекенда

На экране подключения нажмите **«Демо-данные»** — интерфейс работает на
моке (`demo.js`) без backend, данные — в `localStorage`. Удобно для
просмотра всех экранов обеих ролей.

---

## 5. Типовой сценарий

1. **Наниматель** входит, выбирает роль `EMPLOYER` и создаёт вакансию
   (навыки, формат, зарплата, `testMode`: `NONE` / `AUTO` / `CUSTOM`).
2. Бекенд запускает матчинг → во вкладке «Кандидаты» появляются совпадения
   с «✓ / ◐ / ✕» по каждому навыку и баллом.
3. Наниматель отправляет избранным кандидатам тест («📝 Тест») или
   приглашение («✉️ Пригласить»).
4. **Кандидат** видит назначенный тест во вкладке «Тесты», решает и
   отправляет ответы («Отправить ответы», один тест — одна отправка).
5. Бекенд оценивает ответы (AUTO — через Python; CUSTOM — вопросы нанимателя).
6. Наниматель в «Результатах» видит баллы и компетенции, при ошибке оценки —
   «Запустить пересчёт». Итоговый «Рейтинг» — финальные рекомендации.

---

## 6. API бекенда

| Роль | Метод | Путь | Назначение |
| --- | --- | --- | --- |
| Кандидат | `PUT`, `GET` | `/api/v1/candidate/profile` | Сохранить / посмотреть профиль |
| Кандидат | `GET` | `/api/v1/candidate/test-assignments` | Назначенные тесты |
| Кандидат | `GET` | `/api/v1/candidate/test-assignments/{id}` | Вопросы без ключей ответов |
| Кандидат | `POST` | `/api/v1/candidate/test-assignments/{id}/answers` | Отправить все ответы (один раз) |
| Кандидат | `GET` | `/api/v1/candidate/invitations` | Приглашения |
| Кандидат | `PUT` | `/api/v1/candidate/invitations/{id}/decision` | `ACCEPTED` или `DECLINED` |
| Наниматель | `POST`, `GET` | `/api/v1/employer/vacancies` | Создать / список вакансий |
| Наниматель | `GET` | `/api/v1/employer/vacancies/{id}` | Вакансия и статусы обработки |
| Наниматель | `GET` | `/api/v1/employer/vacancies/{id}/matches` | Объяснения матчинга |
| Наниматель | `GET` | `/api/v1/employer/vacancies/{id}/test` | Вопросы теста и ответы |
| Наниматель | `GET` | `/api/v1/employer/vacancies/{id}/ranking` | Финальные и ожидающие |
| Наниматель | `POST` | `/api/v1/employer/vacancies/{id}/assignments` | Отправить тест `{ "candidateId": "..." }` |
| Наниматель | `GET` | `/api/v1/employer/vacancies/{id}/results` | Результаты оценки |
| Наниматель | `POST` | `/api/v1/employer/vacancies/{id}/invitations` | Пригласить финалиста |

**Тест вакансии** (`testMode`):
- `NONE` — без теста;
- `AUTO` — вопросы генерирует Python (OpenRouter); при сбое — повторять
  `POST /{id}/test/generate/retry`;
- `CUSTOM` — вопросы задаёт наниматель: `customQuestions` с `type`,
  `competency`, `text`, `maxScore` и либо `options` + `correctAnswer.optionIndex`,
  либо `rubric`.

Значения по умолчанию для теста: 7 минут, 7 вопросов для AUTO, уровень
`JUNIOR`. Компетенции AUTO берутся из навыков вакансии. Матчинг запускается
при создании; повтор — `POST /{id}/matching/recalculate`. Сбой проверки
ответа — `POST /{id}/assignments/{assignmentId}/score/retry`.

Зарплаты — в рублях в месяц, часы — в неделю. Профиль кандидата обязан
иметь хотя бы один навык и формат работы. Ключи ответов и рубрики кандидату
не передаются никогда.

---

## 7. Структура проекта

```text
staffmatch/
  backend/                  Java 21 (Spring Boot)
    auth/                   MAX-авторизация (фильтр, проверка initData)
    candidate/  assessment/  invitation/  matching/  python/
    ranking/  vacancy/  user/   по фичам → controller/dto/entity/service
  evaluation_service/        Python 3.13 (FastAPI, uv)
    app/                    приложение, схемы, формулы, клиент OpenRouter
    tests/                  автотесты
  frontend/                 MAX Web App (статика, без сборки)
    app.js                  логика и экраны
    api.js                  клиент API (X-Max-Init-Data)
    demo.js                 демо-режим без бекенда
    config.js               базовый URL бекенда
  docs/                     Контракт Python, OpenAPI, описание сервиса
  compose.yaml              Docker Compose: postgres + python + backend
  .env.example              Шаблон окружения (копия → .env)
  README.md                 Этот файл
```

## См. также

- [Передний план интерфейса](frontend/README.md) — экраны, конфигурация.
- [Python-сервис](docs/python-service.md) — формулы матчинга, генерация тестов, проверка, рейтинг.
- [Контракт Java ↔ Python](docs/python-contract-v0.2.md) — JSON-интерфейс.