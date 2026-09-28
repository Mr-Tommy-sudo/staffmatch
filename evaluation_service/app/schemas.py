from __future__ import annotations

from typing import Any, Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


# Java @NotBlank uses String.isBlank; nonbreaking spaces are nonblank there.
NonEmpty = Annotated[str, Field(
    min_length=1,
    pattern=r"[^\t-\r\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]",
)]
Score = Annotated[float, Field(ge=0, le=100)]
Format = Literal["REMOTE", "HYBRID", "OFFICE"]


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


def unique(values: list[str], label: str) -> None:
    if len(values) != len(set(values)):
        raise ValueError(f"Duplicate {label}")


class VacancySkill(Model):
    code: NonEmpty
    minLevel: Annotated[int, Field(ge=1, le=5)]
    required: bool
    weight: Annotated[int, Field(ge=0)]


class CandidateSkill(Model):
    code: NonEmpty
    level: Annotated[int, Field(ge=1, le=5)]


class Vacancy(Model):
    id: NonEmpty
    role: NonEmpty
    city: str | None = None
    workFormat: Format
    hoursMin: Annotated[int, Field(ge=0, le=168)] | None = None
    salaryFrom: Annotated[int, Field(ge=0)] | None = None
    salaryTo: Annotated[int, Field(ge=0)] | None = None
    experienceMonthsMin: Annotated[int, Field(ge=0)] | None = None
    skills: Annotated[list[VacancySkill], Field(min_length=1)]

    @model_validator(mode="after")
    def check_vacancy(self) -> Vacancy:
        unique([skill.code for skill in self.skills], "vacancy skill code")
        if self.salaryFrom is not None and self.salaryTo is not None and self.salaryFrom > self.salaryTo:
            raise ValueError("salaryFrom must not exceed salaryTo")
        return self


class Candidate(Model):
    candidateId: NonEmpty
    city: str | None = None
    workFormats: Annotated[list[Format], Field(min_length=1)]
    availableHours: Annotated[int, Field(ge=0, le=168)] | None = None
    salaryExpectation: Annotated[int, Field(ge=0)] | None = None
    experienceMonths: Annotated[int, Field(ge=0)] | None = None
    portfolioAvailable: bool | None = None
    skills: Annotated[list[CandidateSkill], Field(min_length=1)]

    @model_validator(mode="after")
    def check_candidate(self) -> Candidate:
        unique([skill.code for skill in self.skills], "candidate skill code")
        unique(self.workFormats, "work format")
        return self


class MatchingRequest(Model):
    vacancy: Vacancy
    candidates: Annotated[list[Candidate], Field(max_length=100)]

    @model_validator(mode="after")
    def check_candidates(self) -> MatchingRequest:
        unique([candidate.candidateId for candidate in self.candidates], "candidateId")
        return self


class Explanation(Model):
    field: NonEmpty
    code: NonEmpty
    expected: Any
    actual: Any
    contribution: Score
    explanation: NonEmpty


class Match(Model):
    candidateId: NonEmpty
    eligible: bool
    score: Score
    matched: list[Explanation]
    partial: list[Explanation]
    missing: list[Explanation]


class MatchingResponse(Model):
    vacancyId: NonEmpty
    algorithmVersion: NonEmpty
    matches: list[Match]


class Competency(Model):
    code: NonEmpty
    weight: Annotated[int, Field(ge=0)]


class GenerateRequest(Model):
    vacancyId: NonEmpty
    mode: Literal["AUTO"]
    role: NonEmpty
    level: NonEmpty
    durationMinutes: Annotated[int, Field(ge=5, le=10)]
    questionCount: Annotated[int, Field(ge=6, le=8)]
    competencies: Annotated[list[Competency], Field(min_length=1)]

    @model_validator(mode="after")
    def check_competencies(self) -> GenerateRequest:
        unique([item.code for item in self.competencies], "competency code")
        return self


class CorrectAnswer(Model):
    model_config = ConfigDict(extra="allow")
    optionIndex: Annotated[int, Field(ge=0)]


class ChoiceQuestion(Model):
    questionId: NonEmpty
    type: Literal["SINGLE_CHOICE"] = "SINGLE_CHOICE"
    competency: NonEmpty
    text: NonEmpty
    options: Annotated[list[NonEmpty], Field(min_length=2)]
    correctAnswer: CorrectAnswer
    maxScore: Annotated[int, Field(ge=1, le=100)]


class GenerateResponse(Model):
    testId: NonEmpty
    generationVersion: NonEmpty
    estimatedDurationMinutes: Annotated[int, Field(ge=5, le=10)]
    questions: list[ChoiceQuestion]


class Rubric(Model):
    model_config = ConfigDict(extra="allow")
    maxScore: Annotated[int, Field(ge=1, le=100)] | None = None
    criteria: Annotated[list[NonEmpty], Field(min_length=1)]


class ScoreQuestion(Model):
    questionId: NonEmpty
    type: Literal["SINGLE_CHOICE", "FREE_TEXT"]
    competency: NonEmpty
    text: NonEmpty | None = None
    correctAnswer: CorrectAnswer | None = None
    rubric: Rubric | None = None
    maxScore: Annotated[int, Field(ge=1, le=100)]

    @model_validator(mode="after")
    def check_question(self) -> ScoreQuestion:
        if self.type == "SINGLE_CHOICE" and self.correctAnswer is None:
            raise ValueError("SINGLE_CHOICE needs correctAnswer")
        if self.type == "FREE_TEXT":
            if self.text is None or self.rubric is None:
                raise ValueError("FREE_TEXT needs text and rubric")
            if self.rubric.maxScore is not None and self.rubric.maxScore != self.maxScore:
                raise ValueError("rubric.maxScore must equal maxScore")
        return self


class Answer(Model):
    questionId: NonEmpty
    selectedOptionIndex: Annotated[int, Field(ge=0)] | None = None
    text: Annotated[NonEmpty, Field(max_length=4000)] | None = None


class ScoreRequest(Model):
    assignmentId: NonEmpty
    questions: Annotated[list[ScoreQuestion], Field(min_length=1)]
    answers: list[Answer]

    @model_validator(mode="after")
    def check_answers(self) -> ScoreRequest:
        question_ids = [question.questionId for question in self.questions]
        answer_ids = [answer.questionId for answer in self.answers]
        unique(question_ids, "questionId")
        unique(answer_ids, "answer questionId")
        if sum(question.maxScore for question in self.questions) > 100:
            raise ValueError("Question maxScore values must sum to at most 100")
        if set(question_ids) != set(answer_ids):
            raise ValueError("Answers must match the question IDs exactly")
        answers = {answer.questionId: answer for answer in self.answers}
        for question in self.questions:
            answer = answers[question.questionId]
            if question.type == "SINGLE_CHOICE":
                if answer.selectedOptionIndex is None or answer.text is not None:
                    raise ValueError("SINGLE_CHOICE needs selectedOptionIndex only")
            elif answer.text is None or answer.selectedOptionIndex is not None:
                raise ValueError("FREE_TEXT needs nonempty text only")
        return self


class QuestionResult(Model):
    questionId: NonEmpty
    score: Score
    maxScore: Annotated[int, Field(ge=1, le=100)]
    explanation: NonEmpty


class Breakdown(Model):
    competency: NonEmpty
    score: Score
    maxScore: Annotated[float, Field(gt=0, le=100)]
    percent: Score


class ScoreResponse(Model):
    assignmentId: NonEmpty
    scoringVersion: NonEmpty
    modelVersion: str | None = None
    totalScore: Score
    questions: list[QuestionResult]
    breakdown: list[Breakdown]
    summary: NonEmpty


class Weights(Model):
    matching: Annotated[float, Field(ge=0, le=1)] = 0.6
    assessment: Annotated[float, Field(ge=0, le=1)] = 0.4

    @model_validator(mode="after")
    def check_sum(self) -> Weights:
        if abs(self.matching + self.assessment - 1) > 1e-9:
            raise ValueError("matching and assessment weights must sum to 1")
        return self


class RankCandidate(Model):
    candidateId: NonEmpty
    matchingScore: Score
    testScore: Score | None = None
    testState: Literal["COMPLETED", "WAITING", "NOT_REQUIRED"]

    @model_validator(mode="after")
    def check_state(self) -> RankCandidate:
        if (self.testState == "COMPLETED") != (self.testScore is not None):
            raise ValueError("testScore must be present exactly when testState is COMPLETED")
        return self


class RankingRequest(Model):
    vacancyId: NonEmpty
    weights: Weights = Field(default_factory=Weights)
    candidates: list[RankCandidate]

    @model_validator(mode="after")
    def check_candidates(self) -> RankingRequest:
        unique([candidate.candidateId for candidate in self.candidates], "candidateId")
        return self


class FinalEntry(Model):
    candidateId: NonEmpty
    rank: Annotated[int, Field(ge=1)]
    finalScore: Score
    state: Literal["FINAL", "NO_TEST"]
    components: dict[str, Score]


class WaitingEntry(Model):
    candidateId: NonEmpty
    state: Literal["WAITING_TEST"] = "WAITING_TEST"
    matchingScore: Score


class RankingResponse(Model):
    vacancyId: NonEmpty
    rankingVersion: NonEmpty
    final: list[FinalEntry]
    waiting: list[WaitingEntry]


class ErrorDetail(Model):
    field: str
    message: str


class ErrorResponse(Model):
    requestId: str
    status: int
    error: str
    message: str
    retryable: bool
    details: list[ErrorDetail]
