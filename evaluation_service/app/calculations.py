from __future__ import annotations

from decimal import Decimal, ROUND_HALF_UP

from app.schemas import (
    Breakdown,
    Explanation,
    FinalEntry,
    Match,
    MatchingRequest,
    MatchingResponse,
    QuestionResult,
    RankingRequest,
    RankingResponse,
    ScoreRequest,
    ScoreResponse,
    WaitingEntry,
)


def number(value: float | int) -> Decimal:
    return Decimal(str(value))


def rounded(value: Decimal) -> float:
    return float(value.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))


def matching(request: MatchingRequest) -> MatchingResponse:
    vacancy = request.vacancy
    configured_skill_weight = sum(skill.weight for skill in vacancy.skills)
    total_skill_weight = configured_skill_weight or len(vacancy.skills)
    matches: list[Match] = []

    for candidate in request.candidates:
        levels = {skill.code: skill.level for skill in candidate.skills}
        eligible = vacancy.workFormat in candidate.workFormats and all(
            levels.get(skill.code, 0) >= skill.minLevel
            for skill in vacancy.skills if skill.required
        )
        factors: list[tuple[str, str, object, object, Decimal, Decimal, str]] = []
        for skill in vacancy.skills:
            level = levels.get(skill.code)
            portion = skill.weight if configured_skill_weight else 1
            weight = Decimal(70 * portion) / total_skill_weight
            fraction = min(Decimal(level or 0) / skill.minLevel, Decimal(1))
            factors.append(("skills", skill.code, skill.minLevel, level, weight, fraction,
                            "Обязательный навык" if skill.required else "Дополнительный навык"))

        if vacancy.hoursMin:
            if candidate.availableHours is None:
                factors.append(("availableHours", "NOT_PROVIDED", vacancy.hoursMin, None,
                                Decimal(0), Decimal(0), "Часы не указаны"))
            else:
                factors.append(("availableHours", "HOURS", vacancy.hoursMin,
                                candidate.availableHours, Decimal(10),
                                min(Decimal(candidate.availableHours) / vacancy.hoursMin, Decimal(1)),
                                "Доступные часы в неделю"))

        if vacancy.salaryTo is not None:
            if candidate.salaryExpectation is None:
                factors.append(("salaryExpectation", "NOT_PROVIDED", vacancy.salaryTo, None,
                                Decimal(0), Decimal(0), "Ожидание по зарплате не указано"))
            elif candidate.salaryExpectation <= vacancy.salaryTo:
                factors.append(("salaryExpectation", "SALARY", vacancy.salaryTo,
                                candidate.salaryExpectation, Decimal(10), Decimal(1),
                                "Ожидание укладывается в бюджет"))
            else:
                over = Decimal(candidate.salaryExpectation - vacancy.salaryTo)
                allowance = Decimal(vacancy.salaryTo) / 5
                fraction = max(Decimal(0), Decimal(1) - over / allowance) if allowance else Decimal(0)
                factors.append(("salaryExpectation", "SALARY", vacancy.salaryTo,
                                candidate.salaryExpectation, Decimal(10), fraction,
                                "Ожидание выше бюджета"))

        if vacancy.experienceMonthsMin:
            if candidate.experienceMonths is None:
                factors.append(("experienceMonths", "NOT_PROVIDED", vacancy.experienceMonthsMin,
                                None, Decimal(0), Decimal(0), "Опыт не указан"))
            else:
                factors.append(("experienceMonths", "EXPERIENCE", vacancy.experienceMonthsMin,
                                candidate.experienceMonths, Decimal(5),
                                min(Decimal(candidate.experienceMonths) / vacancy.experienceMonthsMin,
                                    Decimal(1)), "Опыт в месяцах"))

        if vacancy.workFormat != "REMOTE" and vacancy.city and vacancy.city.strip():
            if not candidate.city or not candidate.city.strip():
                factors.append(("city", "NOT_PROVIDED", vacancy.city, None,
                                Decimal(0), Decimal(0), "Город не указан"))
            else:
                same_city = vacancy.city.strip().casefold() == candidate.city.strip().casefold()
                factors.append(("city", "CITY", vacancy.city, candidate.city,
                                Decimal(5), Decimal(int(same_city)), "Город работы"))

        active_weight = sum((weight for _, _, _, _, weight, _, _ in factors), Decimal(0))
        matched: list[Explanation] = []
        partial: list[Explanation] = []
        missing: list[Explanation] = []

        def add(field: str, code: str, expected: object, actual: object,
                weight: Decimal, fraction: Decimal, description: str) -> None:
            contribution = rounded(Decimal(100) * weight * fraction / active_weight)
            item = Explanation(field=field, code=code, expected=expected, actual=actual,
                               contribution=contribution, explanation=description)
            (matched if fraction == 1 and code != "NOT_PROVIDED" else
             missing if fraction == 0 else partial).append(item)

        format_matches = vacancy.workFormat in candidate.workFormats
        add("workFormat", "WORK_FORMAT", vacancy.workFormat, candidate.workFormats,
            Decimal(0), Decimal(int(format_matches)), "Совместимость формата работы")
        for field, code, expected, actual, weight, fraction, description in factors:
            add(field, code, expected, actual, weight, fraction, description)
        if not candidate.portfolioAvailable:
            missing.append(Explanation(field="portfolioAvailable", code="NOT_PROVIDED",
                                       expected="optional", actual=candidate.portfolioAvailable,
                                       contribution=0,
                                       explanation="Портфолио не предоставлено; на балл не влияет"))

        score = rounded(sum((Decimal(100) * weight * fraction / active_weight
                             for _, _, _, _, weight, fraction, _ in factors), Decimal(0)))
        matches.append(Match(candidateId=candidate.candidateId, eligible=eligible, score=score,
                             matched=matched, partial=partial, missing=missing))

    matches.sort(key=lambda item: (-item.score, item.candidateId))
    return MatchingResponse(vacancyId=vacancy.id, algorithmVersion="matching-v1", matches=matches)


def scoring(request: ScoreRequest, graded: dict[str, tuple[int, str]],
            model: str | None) -> ScoreResponse:
    answers = {answer.questionId: answer for answer in request.answers}
    results: list[QuestionResult] = []
    groups: dict[str, tuple[float, int]] = {}
    for question in request.questions:
        if question.type == "SINGLE_CHOICE":
            score = question.maxScore if answers[question.questionId].selectedOptionIndex == question.correctAnswer.optionIndex else 0
            explanation = "Верный ответ" if score else "Ответ не засчитан"
        else:
            score, explanation = graded[question.questionId]
        results.append(QuestionResult(questionId=question.questionId, score=score,
                                      maxScore=question.maxScore, explanation=explanation))
        previous_score, previous_max = groups.get(question.competency, (0, 0))
        groups[question.competency] = (previous_score + score, previous_max + question.maxScore)

    earned = sum(result.score for result in results)
    maximum = sum(result.maxScore for result in results)
    breakdown = [Breakdown(competency=code, score=score, maxScore=max_score,
                           percent=rounded(Decimal(100) * number(score) / max_score))
                 for code, (score, max_score) in sorted(groups.items())]
    total = rounded(Decimal(100) * number(earned) / maximum)
    return ScoreResponse(assignmentId=request.assignmentId,
                         scoringVersion="scoring-v1" + (f"/{model}" if model else ""),
                         modelVersion=model, totalScore=total, questions=results,
                         breakdown=breakdown, summary=f"Набрано {total}% от максимума")


def ranking(request: RankingRequest) -> RankingResponse:
    final: list[FinalEntry] = []
    waiting: list[WaitingEntry] = []
    for candidate in request.candidates:
        if candidate.testState == "WAITING":
            waiting.append(WaitingEntry(candidateId=candidate.candidateId,
                                        matchingScore=candidate.matchingScore))
            continue
        if candidate.testState == "NOT_REQUIRED":
            final.append(FinalEntry(candidateId=candidate.candidateId, rank=1,
                                    finalScore=rounded(number(candidate.matchingScore)),
                                    state="NO_TEST", components={"matching": candidate.matchingScore}))
            continue
        total = (number(candidate.matchingScore) * number(request.weights.matching)
                 + number(candidate.testScore) * number(request.weights.assessment))
        final.append(FinalEntry(candidateId=candidate.candidateId, rank=1,
                                finalScore=rounded(total), state="FINAL",
                                components={"matching": candidate.matchingScore,
                                            "assessment": candidate.testScore}))
    final.sort(key=lambda item: (-item.finalScore, item.candidateId))
    for position, item in enumerate(final, 1):
        item.rank = position
    waiting.sort(key=lambda item: item.candidateId)
    return RankingResponse(vacancyId=request.vacancyId, rankingVersion="ranking-v1",
                           final=final, waiting=waiting)
