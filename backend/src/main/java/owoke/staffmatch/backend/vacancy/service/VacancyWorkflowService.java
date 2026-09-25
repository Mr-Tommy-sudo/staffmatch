package owoke.staffmatch.backend.vacancy.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.assessment.service.TestDefinitionService;
import owoke.staffmatch.backend.matching.service.MatchingService;
import owoke.staffmatch.backend.ranking.service.RankingService;
import owoke.staffmatch.backend.vacancy.dto.VacancyCreateRequest;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;

@Service
public class VacancyWorkflowService {
    private final VacancyRepository vacancies;
    private final TestDefinitionService tests;
    private final MatchingService matching;
    private final RankingService ranking;

    public VacancyWorkflowService(VacancyRepository vacancies, TestDefinitionService tests,
                                  MatchingService matching, RankingService ranking) {
        this.vacancies = vacancies; this.tests = tests; this.matching = matching; this.ranking = ranking;
    }

    public void initialize(UUID id, VacancyCreateRequest request) {
        var vacancy = vacancies.find(id).orElseThrow();
        if (request.testMode() == VacancyCreateRequest.TestMode.CUSTOM) {
            try {
                tests.createCustom(id, request);
                vacancies.generationStatus(id, "READY", null);
            } catch (RuntimeException exception) {
                vacancies.generationStatus(id, "FAILED", exception.getMessage());
            }
        } else if (request.testMode() == VacancyCreateRequest.TestMode.AUTO) {
            generate(vacancy);
        }
        recalculateMatching(id);
    }

    public void generate(Vacancy vacancy) {
        if (!vacancy.testMode().equals("AUTO")) return;
        try {
            tests.generate(vacancy);
            vacancies.generationStatus(vacancy.id(), "READY", null);
        } catch (RuntimeException exception) {
            vacancies.generationStatus(vacancy.id(), "FAILED", exception.getMessage());
        }
    }

    public void recalculateMatching(UUID id) {
        var vacancy = vacancies.find(id).orElseThrow();
        try {
            matching.calculate(vacancy);
            vacancies.matchingStatus(id, "READY", null);
            recalculateRanking(id);
        } catch (RuntimeException exception) {
            vacancies.matchingStatus(id, "FAILED", exception.getMessage());
        }
    }

    public void recalculateRanking(UUID id) {
        var vacancy = vacancies.find(id).orElseThrow();
        if (!vacancy.matchingStatus().equals("READY")) return;
        if (!vacancy.testMode().equals("NONE") && !vacancy.generationStatus().equals("READY")) return;
        try {
            ranking.calculate(vacancy);
            vacancies.rankingStatus(id, "READY", null);
        } catch (RuntimeException exception) {
            vacancies.rankingStatus(id, "FAILED", exception.getMessage());
        }
    }
}
