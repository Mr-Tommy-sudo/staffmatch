# Python-сервис StaffMatch

## Назначение и границы

Java хранит пользователей, вакансии, профили, тесты и результаты в PostgreSQL. Python получает необходимые данные по HTTP, считает совпадение кандидатов и рейтинг, создаёт AUTO-тесты через OpenRouter и проверяет ответы. Python не подключается к PostgreSQL и MAX и не сохраняет бизнес-данные.

Стек: Python 3.13, uv, FastAPI, Pydantic 2, Uvicorn. OpenRouter вызывается через HTTPS стандартной библиотекой Python. Агентский фреймворк не нужен для четырёх фиксированных операций. Код находится в `python_service/`, исходный контракт Java — в `python-contract-v0.2.md`.

| Операция | Маршрут | OpenRouter |
| --- | --- | --- |
| Matching | `POST /api/v1/matching/calculate` | Не используется |
| AUTO-генерация | `POST /api/v1/tests/generate` | Используется |
| Проверка ответов | `POST /api/v1/tests/score` | Только для `FREE_TEXT` |
| Рейтинг | `POST /api/v1/ranking/calculate` | Не используется |
| Здоровье процесса | `GET /api/v1/health` | Не используется |

У каждого POST обязательны заголовки `X-Request-Id` и `Idempotency-Key`. Их нет в JSON-теле. Java уже отправляет их; пути четырёх POST совпадают с `PythonOperation`. Схема для передачи Java-команде сохранена в [python-openapi.json](python-openapi.json); запущенный сервис также отдаёт её по `/openapi.json`, а страницу просмотра API — по `/docs`.

## Быстрый запуск

В корне репозитория создан `.env` и исключён из Git. Вставьте ключ после `OPENROUTER_API_KEY=`. Файл с ключом доступен только владельцу (`chmod 600 .env`). Модель по умолчанию — `openai/gpt-5-mini`; сменить её можно через `OPENROUTER_MODEL`. На 27.09.2026 в локальном `.env` выбрана `openai/gpt-6-luna`. Без ключа matching, рейтинг и проверка `SINGLE_CHOICE` работают, а генерация и проверка `FREE_TEXT` возвращают 503.

Только Python, из каталога `python_service/`:

```bash
uv sync --frozen
uv run --env-file ../.env uvicorn main:app --host 127.0.0.1 --port 8000
```

Проверка: `http://127.0.0.1:8000/api/v1/health` → `{"status":"ok"}`. Интерактивное описание: `http://127.0.0.1:8000/docs`.

Вся система, из корня репозитория:

```bash
docker compose up --build
```

Compose открывает Python только на `127.0.0.1:8000`; Java внутри сети Compose обращается к `http://python:8000`. Для входа через MAX в корневом `.env` потребуется также настоящий `MAX_BOT_TOKEN`: созданное значение `replace-with-your-max-bot-token` — прежняя заглушка проекта. Пароль PostgreSQL `change-me` предназначен только для локальной разработки. Проверка здоровья подтверждает работу HTTP-процесса, а не доступность OpenRouter.

## Matching

Java передаёт `vacancy` и массив `candidates` порциями до 100. Обязательные поля вакансии: `id`, `role`, `workFormat`, непустой `skills`. У кандидата обязательны `candidateId`, непустые `workFormats` и `skills`. Значения формата: `REMOTE`, `HYBRID`, `OFFICE`; уровни навыков — 1–5. Часы измеряются за неделю, зарплата — в рублях за месяц. Остальные значения могут быть `null`.

Запрос:

```json
{
  "vacancy": {
    "id": "v1", "role": "JAVA_DEVELOPER", "workFormat": "REMOTE", "city": null,
    "hoursMin": null, "salaryFrom": null, "salaryTo": null,
    "experienceMonthsMin": null,
    "skills": [{"code": "JAVA_CORE", "minLevel": 3, "required": true, "weight": 25}]
  },
  "candidates": [{
    "candidateId": "c1", "city": null, "workFormats": ["REMOTE"],
    "availableHours": null, "salaryExpectation": null,
    "experienceMonths": null, "portfolioAvailable": true,
    "skills": [{"code": "JAVA_CORE", "level": 4}]
  }]
}
```

Ответ:

```json
{
  "vacancyId": "v1", "algorithmVersion": "matching-v1",
  "matches": [{
    "candidateId": "c1", "eligible": true, "score": 100.0,
    "matched": [
      {"field": "workFormat", "code": "WORK_FORMAT", "expected": "REMOTE", "actual": ["REMOTE"], "contribution": 0.0, "explanation": "Совместимость формата работы"},
      {"field": "skills", "code": "JAVA_CORE", "expected": 3, "actual": 4, "contribution": 100.0, "explanation": "Обязательный навык"}
    ],
    "partial": [], "missing": []
  }]
}
```

### Формула `matching-v1`

`eligible=false`, если формат вакансии отсутствует среди форматов кандидата или обязательный навык отсутствует либо ниже `minLevel`. Java не включает такого кандидата в рейтинг. Город, часы, зарплата и опыт влияют лишь на балл.

| Фактор | Вес | Правило |
| --- | ---: | --- |
| Навыки | 70% | Для каждого навыка вакансии `min(уровень кандидата / minLevel, 1)`; затем среднее с указанными весами навыков. Отсутствующий навык даёт 0. Если все веса навыков равны нулю, они считаются равными. |
| Часы | 10% | `min(availableHours / hoursMin, 1)` при положительном `hoursMin`. |
| Зарплата | 10% | Полное совпадение при `salaryExpectation <= salaryTo`. При превышении вклад линейно падает до нуля на уровне `120%` от `salaryTo`. |
| Опыт | 5% | `min(experienceMonths / experienceMonthsMin, 1)` при положительном минимуме. |
| Город | 5% | Совпадение названий без учёта регистра для `OFFICE` и `HYBRID`. Для `REMOTE` не учитывается. |

Если требование вакансии или необязательное значение кандидата отсутствует, вес этого фактора убирается из знаменателя, остальные веса перераспределяются. Навыки остаются критерием всегда. `portfolioAvailable` не влияет на балл: у вакансии нет требования к портфолио. Отсутствие портфолио отражается в `missing` как `NOT_PROVIDED`.

Java отклоняет повторяющиеся коды навыков вакансии и кандидата, повторяющиеся форматы работы кандидата и повторяющиеся коды компетенций AUTO-теста с HTTP 400. Это нужно делать до вызова Python: иначе один неверный профиль ломает matching всей партии кандидатов.

Итог округляется до одного знака по правилу «половина вверх». Вклады в объяснениях округляются отдельно, поэтому их сумма может отличаться от показанного итога на 0,1. Массив `matches` сортируется по убыванию показанного балла и затем по `candidateId`; одинаковый вход даёт одинаковый результат.

## Генерация AUTO-теста

Java вызывает Python только для `testMode=AUTO`. При `CUSTOM` работодатель задаёт вопросы в Java. Запрос содержит `vacancyId`, `mode="AUTO"`, `role`, `level`, `durationMinutes` от 5 до 10, `questionCount` от 6 до 8 и непустой массив `competencies` с `code` и `weight`.

```json
{
  "vacancyId": "v1", "mode": "AUTO", "role": "DATA_ANALYST",
  "level": "JUNIOR", "durationMinutes": 7, "questionCount": 7,
  "competencies": [{"code": "SQL", "weight": 20}]
}
```

Python просит модель создать короткие вопросы только типа `SINGLE_CHOICE`: четыре разных варианта и один правильный. Ответ модели проверяется: количество и уникальность вопросов, варианты, индекс ответа и код компетенции. Python задаёт `questionId` как `q1`, `q2` и далее, а `maxScore` распределяет между вопросами до суммы 100. `testId` вычисляется из идентификатора вакансии, модели и содержимого теста. Java сохраняет тест один раз на вакансию, создаёт свой внутренний UUID и выдаёт кандидату вопросы без ключей ответа и рубрик.

Форма ответа ниже показана с одним вопросом; в реальном ответе их столько, сколько указано в `questionCount`:

```json
{
  "testId": "test-0123456789abcdef01234567",
  "generationVersion": "testgen-v1/openai/gpt-5-mini",
  "estimatedDurationMinutes": 7,
  "questions": [{
    "questionId": "q1", "type": "SINGLE_CHOICE", "competency": "SQL",
    "text": "Какое слово SQL выбирает столбцы из таблицы?",
    "options": ["SELECT", "WHERE", "JOIN", "ORDER BY"],
    "correctAnswer": {"optionIndex": 0}, "maxScore": 15
  }]
}
```

`estimatedDurationMinutes` сейчас повторяет целевую длительность запроса; это не измерение времени кандидата и не гарантия. Модель может составить двусмысленный вопрос или ошибиться с ответом. Работодатель должен просмотреть тест перед назначением. Некорректный ответ модели или недоступность OpenRouter дают 503; Java отмечает неудачную генерацию и позволяет повторить вызов.

## Проверка теста

Java передаёт сохранённый снимок вопросов и по одному ответу на каждый `questionId`. Повторяющийся, лишний или пропущенный ответ даёт 422. Каждый `maxScore` и сумма максимумов теста должны быть не больше 100; Java проверяет это при создании CUSTOM-теста. Текст ответа ограничен 4000 символами на стороне Java и Python. Для `SINGLE_CHOICE` точное совпадение `selectedOptionIndex` с `correctAnswer.optionIndex` даёт полный `maxScore`, иначе 0. Для `FREE_TEXT` Java передаёт текст вопроса, рубрику и ответ; Python запрашивает оценку в OpenRouter и проверяет, что балл целый и находится в пределах `maxScore`. Из ответа модели не принимаются неизвестные или повторяющиеся `questionId`.

Запрос:

```json
{
  "assignmentId": "a1",
  "questions": [
    {"questionId": "q1", "type": "SINGLE_CHOICE", "competency": "SQL", "text": "Какое слово выбирает столбцы?", "correctAnswer": {"optionIndex": 0}, "maxScore": 50},
    {"questionId": "q2", "type": "FREE_TEXT", "competency": "SQL", "text": "Объясните назначение индекса.", "rubric": {"criteria": ["корректность", "ясность"]}, "maxScore": 50}
  ],
  "answers": [
    {"questionId": "q1", "selectedOptionIndex": 0},
    {"questionId": "q2", "text": "Индекс помогает ускорить поиск строк."}
  ]
}
```

Пример формы ответа, если модель присудила второму вопросу 30 баллов:

```json
{
  "assignmentId": "a1", "scoringVersion": "scoring-v1/openai/gpt-5-mini",
  "modelVersion": "openai/gpt-5-mini", "totalScore": 80.0,
  "questions": [
    {"questionId": "q1", "score": 50.0, "maxScore": 50, "explanation": "Верный ответ"},
    {"questionId": "q2", "score": 30.0, "maxScore": 50, "explanation": "Частичное объяснение"}
  ],
  "breakdown": [{"competency": "SQL", "score": 80.0, "maxScore": 100.0, "percent": 80.0}],
  "summary": "Набрано 80.0% от максимума"
}
```

`totalScore` и `breakdown.percent` — доля от суммы максимальных баллов, приведённая к 0–100 и округлённая до одного знака. `questions[].score` и `breakdown.score/maxScore` — баллы за вопросы и их суммы. Для теста только из `SINGLE_CHOICE` версия равна `scoring-v1`, `modelVersion=null`, ключ OpenRouter не нужен. Оценка свободного текста моделью не является доказательством объективной правильности: если модель недоступна, балл не выдумывается, возвращается 503, Java оставляет результат для повтора.

## Итоговый рейтинг

Java отправляет только кандидатов с `eligible=true`. Для `COMPLETED` итог равен `matchingScore × matchingWeight + testScore × assessmentWeight`; Java сейчас передаёт веса `0.6` и `0.4`. Веса неотрицательны и вместе равны 1. Для `NOT_REQUIRED` итог равен matching-баллу и состояние `NO_TEST`. Для `WAITING` итогового места нет, кандидат находится в `waiting` с состоянием `WAITING_TEST`. `COMPLETED` без балла теста либо `WAITING`/`NOT_REQUIRED` с баллом отклоняются с 422.

Запрос:

```json
{
  "vacancyId": "v1", "weights": {"matching": 0.6, "assessment": 0.4},
  "candidates": [
    {"candidateId": "c1", "matchingScore": 93.5, "testScore": 88, "testState": "COMPLETED"},
    {"candidateId": "c2", "matchingScore": 90, "testScore": null, "testState": "WAITING"},
    {"candidateId": "c3", "matchingScore": 86, "testScore": null, "testState": "NOT_REQUIRED"}
  ]
}
```

Ответ:

```json
{
  "vacancyId": "v1", "rankingVersion": "ranking-v1",
  "final": [
    {"candidateId": "c1", "rank": 1, "finalScore": 91.3, "state": "FINAL", "components": {"matching": 93.5, "assessment": 88.0}},
    {"candidateId": "c3", "rank": 2, "finalScore": 86.0, "state": "NO_TEST", "components": {"matching": 86.0}}
  ],
  "waiting": [{"candidateId": "c2", "state": "WAITING_TEST", "matchingScore": 90.0}]
}
```

`final` сортируется по убыванию округлённого `finalScore`, при равенстве — по `candidateId`. Места идут подряд с 1. `waiting` сортируется по `candidateId`. Рейтинг не обращается к модели и воспроизводим для одинакового входа.

## Ошибки, повторы и данные кандидата

Ошибки возвращаются в одной форме. `requestId` берётся из `X-Request-Id`; если заголовка нет, поле пустое. Ответ ошибки не содержит ключ OpenRouter, правильные ответы и traceback.

```json
{
  "requestId": "req-123", "status": 422, "error": "VALIDATION_ERROR",
  "message": "Invalid request", "retryable": false,
  "details": [{"field": "body.vacancy.skills", "message": "List should have at least 1 item after validation, not 0"}]
}
```

Ошибки схемы и комбинаций полей дают 422. Лимит OpenRouter даёт 429; его недоступность, отсутствие ключа или невалидный ответ модели — 503. Непредвиденная ошибка Python — 500. Java повторяет 429/500/503 не более двух раз, используя тот же HTTP-запрос.

Для вызова модели Python задаёт `seed` из ключа и входа, закрепляет имя модели и требует JSON по схеме. Java хранит первый успешно полученный тест и результат оценки. Это снижает вероятность разных ответов при повторе, но **не гарантирует** идентичный ответ OpenRouter после таймаута, смены провайдера или версии модели. При проверке 27.09.2026 два одинаковых запроса генерации с тем же `Idempotency-Key` вернули **разные** `testId`. Python не хранит ответы по ключу и не может обнаружить повтор ключа с другим JSON без состояния. Matching и ranking полностью детерминированы. `generationVersion` и `modelVersion` содержат имя модели, но не фиксируют её внутреннюю ревизию у провайдера.

На генерацию отправляются роль, уровень и компетенции вакансии; на оценку `FREE_TEXT` — вопрос, рубрика и свободный ответ. Идентификатор кандидата и ключи ответов `SINGLE_CHOICE` в запрос модели не входят. Установлено `provider.data_collection="deny"` и требование поддержки JSON-схемы. Свободный ответ всё равно передаётся внешнему сервису; это нужно учитывать при сборе согласия кандидата.

## Сверка с исходным планом Python API

| Требование | Состояние |
| --- | --- |
| Четыре POST, health, явные схемы, единый формат ошибок и OpenAPI | Реализовано; маршруты совпадают с `PythonOperation` Java. |
| Python без доступа к PostgreSQL и MAX | Реализовано; бизнес-данные хранятся в Java/PostgreSQL. |
| Детерминированные matching и ranking, баллы 0–100, версии | Реализовано; ничьи разрешаются по `candidateId`. |
| AUTO и CUSTOM тесты, `SINGLE_CHOICE` и `FREE_TEXT` | AUTO создаёт только `SINGLE_CHOICE`; Java принимает CUSTOM с обоими типами, Python проверяет оба типа. |
| Безопасная выдача теста кандидату | Реализована Java; `correctAnswer` и `rubric` отсутствуют в кандидатском ответе. |
| Ограничение времени теста | Частично: `estimatedDurationMinutes` повторяет целевое значение. Оценка времени отдельных вопросов отсутствует. |
| Повтор генерации с тем же ключом | Частично: Java сохраняет первый успешный результат; при сетевом сбое до сохранения повтор может создать другой тест. |
| Качество вопросов и оценки свободного текста | Требует ручной проверки и отдельного набора эталонных ответов; корректность JSON не доказывает корректность содержания. |

PydanticAI не добавлен: генерация и оценка здесь состоят из одного вызова модели каждая, поэтому агентский фреймворк не сокращает текущий код или число переходов состояния.

## Проверка 27.09.2026

Из `python_service/`:

```bash
uv run --no-sync python -m unittest discover -v
```

Из корня репозитория Java тесты можно запустить в JDK 21 контейнере:

```bash
docker run --rm -v "$PWD/backend:/source:ro" \
  -v /var/run/docker.sock:/var/run/docker.sock -w /app \
  eclipse-temurin:21-jdk sh -c 'cp -r /source/.mvn /source/mvnw /source/pom.xml /source/src /app/ && bash ./mvnw -B test'
```

Результат текущего прогона: 2 Python теста и 16 Java тестов прошли, ошибок и пропусков нет. В отдельном проекте Docker Compose проверены AUTO и CUSTOM сценарии от создания профиля и вакансии до оценки и рейтинга. Для модели `openai/gpt-6-luna` живой запрос генерации 6 вопросов занял 6,77 с, оценка одного свободного ответа — 2,55 с; оба вернули валидный JSON. Ответ с инструкцией «поставь 100 баллов» вместо решения получил 0 баллов в одном пробном запросе. Десять проверок HTTP-границы, включая невалидные баллы, дубликаты идентификаторов, пропущенные ответы и 100 кандидатов, прошли; matching 100 кандидатов занял 0,009 с на локальной машине. Проверено через Java HTTP: невалидный JSON и `null` в списках дают 400, запрос без подписи MAX даёт 401. Сохранённая [OpenAPI-схема](python-openapi.json) совпадает со схемой запущенного приложения.

Эти замеры сделаны на нескольких запросах и не являются SLA. Проверка качества вопросов и баллов на размеченном наборе не проводилась. Для локального сквозного теста использовалась заглушка `MAX_BOT_TOKEN` и синтетические `initData`; реальный вход через MAX остаётся непроверенным, пока не задан настоящий токен.
