from __future__ import annotations

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from logic import matching, ranking, scoring
from models import (
    ErrorResponse,
    GenerateRequest,
    GenerateResponse,
    MatchingRequest,
    MatchingResponse,
    RankingRequest,
    RankingResponse,
    ScoreRequest,
    ScoreResponse,
)
from openrouter import ModelUnavailable, generate, grade_free_text


app = FastAPI(title="StaffMatch Python API", version="0.1.0")
ERRORS = {status: {"model": ErrorResponse} for status in (400, 422, 429, 500, 503)}


class InvalidHeader(Exception):
    pass


def request_headers(x_request_id: str = Header(alias="X-Request-Id"),
                    idempotency_key: str = Header(alias="Idempotency-Key")) -> tuple[str, str]:
    if not x_request_id.strip() or not idempotency_key.strip():
        raise InvalidHeader()
    return x_request_id, idempotency_key


def error(request: Request, status: int, code: str, message: str,
          details: list[dict] | None = None) -> JSONResponse:
    return JSONResponse(status_code=status, content={
        "requestId": request.headers.get("X-Request-Id", ""),
        "status": status, "error": code, "message": message,
        "retryable": status in (429, 500, 503), "details": details or [],
    })


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exception: RequestValidationError) -> JSONResponse:
    details = [{"field": ".".join(map(str, issue["loc"])), "message": issue["msg"]}
               for issue in exception.errors()]
    return error(request, 422, "VALIDATION_ERROR", "Invalid request", details)


@app.exception_handler(InvalidHeader)
async def invalid_header(request: Request, _: InvalidHeader) -> JSONResponse:
    return error(request, 422, "VALIDATION_ERROR", "Request headers must be nonempty")


@app.exception_handler(ModelUnavailable)
async def model_unavailable(request: Request, exception: ModelUnavailable) -> JSONResponse:
    return error(request, exception.status, "MODEL_UNAVAILABLE",
                 "OpenRouter is unavailable or returned an invalid result")


@app.exception_handler(HTTPException)
async def http_error(request: Request, exception: HTTPException) -> JSONResponse:
    return error(request, exception.status_code, "HTTP_ERROR", str(exception.detail))


@app.exception_handler(Exception)
async def internal_error(request: Request, _: Exception) -> JSONResponse:
    return error(request, 500, "INTERNAL_ERROR", "Internal service error")


@app.get("/api/v1/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/v1/matching/calculate", response_model=MatchingResponse, responses=ERRORS)
def calculate_matching(body: MatchingRequest,
                       _: tuple[str, str] = Depends(request_headers)) -> MatchingResponse:
    return matching(body)


@app.post("/api/v1/tests/generate", response_model=GenerateResponse, responses=ERRORS)
def generate_test(body: GenerateRequest,
                  headers: tuple[str, str] = Depends(request_headers)) -> GenerateResponse:
    return generate(body, headers[1])


@app.post("/api/v1/tests/score", response_model=ScoreResponse, responses=ERRORS)
def score_test(body: ScoreRequest,
               headers: tuple[str, str] = Depends(request_headers)) -> ScoreResponse:
    graded, model = grade_free_text(body, headers[1])
    return scoring(body, graded, model)


@app.post("/api/v1/ranking/calculate", response_model=RankingResponse, responses=ERRORS)
def calculate_ranking(body: RankingRequest,
                      _: tuple[str, str] = Depends(request_headers)) -> RankingResponse:
    return ranking(body)
