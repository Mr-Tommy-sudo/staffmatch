import unittest
from unittest.mock import patch

from logic import matching, ranking, scoring
from main import app
from models import GenerateRequest, MatchingRequest, RankingRequest, ScoreRequest
from openrouter import generate


class ServiceCheck(unittest.TestCase):
    def test_java_contract_calculations(self):
        match = matching(MatchingRequest.model_validate({
            "vacancy": {"id": "v1", "role": "JAVA_DEVELOPER", "workFormat": "REMOTE",
                        "hoursMin": 20, "salaryTo": 80000, "experienceMonthsMin": 6,
                        "skills": [{"code": "JAVA_CORE", "minLevel": 3, "required": True, "weight": 25},
                                   {"code": "SQL", "minLevel": 2, "required": False, "weight": 20}]},
            "candidates": [{"candidateId": "c1", "workFormats": ["REMOTE"],
                            "availableHours": 25, "salaryExpectation": 75000,
                            "experienceMonths": 4, "portfolioAvailable": True,
                            "skills": [{"code": "JAVA_CORE", "level": 4},
                                       {"code": "SQL", "level": 1}]},
                           {"candidateId": "c2", "workFormats": ["REMOTE"],
                            "skills": [{"code": "JAVA_CORE", "level": 2}]}],
        }))
        self.assertEqual([item.candidateId for item in match.matches], ["c1", "c2"])
        self.assertTrue(match.matches[0].eligible)
        self.assertEqual(match.matches[0].score, 81.9)
        self.assertFalse(match.matches[1].eligible)
        self.assertTrue(any(item.code == "NOT_PROVIDED" for item in match.matches[1].missing))

        scored = scoring(ScoreRequest.model_validate({
            "assignmentId": "a1",
            "questions": [{"questionId": "q1", "type": "SINGLE_CHOICE",
                           "competency": "SQL", "correctAnswer": {"optionIndex": 1},
                           "maxScore": 50},
                          {"questionId": "q2", "type": "FREE_TEXT", "text": "Объясните индекс",
                           "competency": "SQL", "rubric": {"criteria": ["точность"]},
                           "maxScore": 50}],
            "answers": [{"questionId": "q1", "selectedOptionIndex": 1},
                        {"questionId": "q2", "text": "Индекс ускоряет поиск."}],
        }), {"q2": (30, "Частичное объяснение")}, "openai/gpt-5-mini")
        self.assertEqual(scored.totalScore, 80.0)
        self.assertEqual(scored.breakdown[0].percent, 80.0)

        ranked = ranking(RankingRequest.model_validate({
            "vacancyId": "v1", "weights": {"matching": 0.6, "assessment": 0.4},
            "candidates": [{"candidateId": "c1", "matchingScore": 93.5,
                            "testScore": 88, "testState": "COMPLETED"},
                           {"candidateId": "c2", "matchingScore": 90,
                            "testState": "WAITING"},
                           {"candidateId": "c3", "matchingScore": 86,
                            "testState": "NOT_REQUIRED"}],
        }))
        self.assertEqual(ranked.final[0].finalScore, 91.3)
        self.assertEqual(ranked.final[1].state, "NO_TEST")
        self.assertEqual(ranked.waiting[0].candidateId, "c2")
        self.assertEqual(len(app.openapi()["paths"]), 5)

    def test_generated_questions_fit_java_schema(self):
        draft = {"questions": [{"competency": "SQL", "text": f"Вопрос {i}",
                                "options": ["A", "B", "C", "D"], "correctOptionIndex": 1}
                               for i in range(6)]}
        request = GenerateRequest.model_validate({
            "vacancyId": "v1", "mode": "AUTO", "role": "ANALYST", "level": "JUNIOR",
            "durationMinutes": 6, "questionCount": 6,
            "competencies": [{"code": "SQL", "weight": 1}],
        })
        with patch("openrouter.complete", return_value=(draft, "openai/gpt-5-mini")):
            result = generate(request, "testgen:v1")
            another_vacancy = generate(request.model_copy(update={"vacancyId": "v2"}), "testgen:v2")
        self.assertEqual(sum(item.maxScore for item in result.questions), 100)
        self.assertEqual(result.questions[0].correctAnswer.optionIndex, 1)
        self.assertNotEqual(result.testId, another_vacancy.testId)


if __name__ == "__main__":
    unittest.main()
