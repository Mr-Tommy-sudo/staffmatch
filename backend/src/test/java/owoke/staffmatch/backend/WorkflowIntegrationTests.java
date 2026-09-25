package owoke.staffmatch.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import owoke.staffmatch.backend.auth.filter.MaxAuthenticationFilter;
import owoke.staffmatch.backend.auth.support.MaxInitDataFixtures;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.python.PythonGateway;
import owoke.staffmatch.backend.python.PythonOperation;
import owoke.staffmatch.backend.python.PythonServiceException;
import tools.jackson.databind.JsonNode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "MAX_BOT_TOKEN=" + MaxInitDataFixtures.BOT_TOKEN)
@AutoConfigureMockMvc
class WorkflowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JsonSupport json;
    @MockitoBean PythonGateway python;

    @BeforeEach
    void pythonStub() {
        when(python.call(any(), any(), anyString())).thenAnswer(invocation -> {
            PythonOperation operation = invocation.getArgument(0);
            JsonNode body = json.tree(invocation.getArgument(1));
            return switch (operation) {
                case MATCHING -> matching(body);
                case GENERATE -> generatedTest(body);
                case SCORE -> score(body);
                case RANK -> rank(body);
            };
        });
    }

    @Test
    void customTestToInvitationAndOwnership() throws Exception {
        long candidate = 810001L;
        long employer = 810002L;
        role(candidate, "CANDIDATE");
        role(employer, "EMPLOYER");
        mvc.perform(put("/api/v1/candidate/profile").header(header(), signed(candidate))
                        .contentType("application/json").content("""
                        {"city":"Moscow","workFormats":["HYBRID"],"availableHours":25,
                         "salaryExpectation":75000,"experienceMonths":4,
                         "skills":[{"code":"JAVA_CORE","level":4}]}
                        """)).andExpect(status().isOk());
        JsonNode candidateProfile = response(get("/api/v1/candidate/profile")
                .header(header(), signed(candidate)));
        String candidateId = candidateProfile.path("candidateId").asText();

        JsonNode vacancy = response(post("/api/v1/employer/vacancies").header(header(), signed(employer))
                .contentType("application/json").content("""
                        {"role":"JAVA_DEVELOPER","city":"Moscow","workFormat":"HYBRID",
                         "skills":[{"code":"JAVA_CORE","minLevel":3,"required":true,"weight":25}],
                         "testMode":"CUSTOM","durationMinutes":7,
                         "customQuestions":[{"type":"SINGLE_CHOICE","competency":"JAVA_CORE",
                         "text":"Which collection is unique?","options":["List","Set"],
                         "correctAnswer":{"optionIndex":1},"maxScore":10}]}
                        """));
        String vacancyId = vacancy.path("id").asText();
        assertTrue(vacancy.path("matchingStatus").asText().equals("READY"));
        assertTrue(vacancy.path("rankingStatus").asText().equals("READY"));

        JsonNode employerTest = response(get("/api/v1/employer/vacancies/{id}/test", vacancyId)
                .header(header(), signed(employer)));
        assertTrue(json.write(employerTest).contains("correctAnswer"));

        response(get("/api/v1/employer/vacancies/{id}/matches", vacancyId)
                .header(header(), signed(employer)));
        JsonNode waiting = response(get("/api/v1/employer/vacancies/{id}/ranking", vacancyId)
                .header(header(), signed(employer)));
        assertTrue(waiting.path("waiting").size() == 1);

        JsonNode assignment = response(post("/api/v1/employer/vacancies/{id}/assignments", vacancyId)
                .header(header(), signed(employer)).contentType("application/json")
                .content("{\"candidateId\":\"" + candidateId + "\"}"));
        String assignmentId = assignment.path("id").asText();
        JsonNode candidateTest = response(get("/api/v1/candidate/test-assignments/{id}", assignmentId)
                .header(header(), signed(candidate)));
        String candidateJson = json.write(candidateTest);
        assertFalse(candidateJson.contains("correctAnswer"));
        assertFalse(candidateJson.contains("rubric"));

        JsonNode submission = response(post("/api/v1/candidate/test-assignments/{id}/answers", assignmentId)
                .header(header(), signed(candidate)).contentType("application/json")
                .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionIndex\":1}]}"));
        assertTrue(submission.path("submitted").booleanValue());
        assertTrue(submission.path("status").asText().equals("SCORED"));
        response(post("/api/v1/candidate/test-assignments/{id}/answers", assignmentId)
                .header(header(), signed(candidate)).contentType("application/json")
                .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionIndex\":1}]}"));
        mvc.perform(post("/api/v1/candidate/test-assignments/{id}/answers", assignmentId)
                        .header(header(), signed(candidate)).contentType("application/json")
                        .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionIndex\":0}]}"))
                .andExpect(status().isConflict());
        JsonNode finalRank = response(get("/api/v1/employer/vacancies/{id}/ranking", vacancyId)
                .header(header(), signed(employer)));
        assertTrue(finalRank.path("final").size() == 1);

        JsonNode invitation = response(post("/api/v1/employer/vacancies/{id}/invitations", vacancyId)
                .header(header(), signed(employer)).contentType("application/json")
                .content("{\"candidateId\":\"" + candidateId + "\"}"));
        response(put("/api/v1/candidate/invitations/{id}/decision", invitation.path("id").asText())
                .header(header(), signed(candidate)).contentType("application/json")
                .content("{\"decision\":\"ACCEPTED\"}"));
        mvc.perform(get("/api/v1/employer/vacancies/{id}", vacancyId)
                .header(header(), signed(candidate))).andExpect(status().isForbidden());
        role(810005L, "EMPLOYER");
        mvc.perform(get("/api/v1/employer/vacancies/{id}", vacancyId)
                .header(header(), signed(810005L))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/employer/vacancies/{id}/test", vacancyId)
                .header(header(), signed(810005L))).andExpect(status().isNotFound());
    }

    @Test
    void noneAndAutoModes() throws Exception {
        long employer = 810003L;
        role(employer, "EMPLOYER");
        JsonNode noTest = response(post("/api/v1/employer/vacancies").header(header(), signed(employer))
                .contentType("application/json").content("""
                        {"role":"JAVA_DEVELOPER","workFormat":"REMOTE",
                         "skills":[{"code":"JAVA_CORE","minLevel":2,"required":true,"weight":25}],
                         "testMode":"NONE"}
                        """));
        assertTrue(noTest.path("rankingStatus").asText().equals("READY"));
        JsonNode auto = response(post("/api/v1/employer/vacancies").header(header(), signed(employer))
                .contentType("application/json").content("""
                        {"role":"JAVA_DEVELOPER","workFormat":"REMOTE",
                         "skills":[{"code":"JAVA_CORE","minLevel":2,"required":true,"weight":25}],
                         "testMode":"AUTO"}
                        """));
        assertTrue(auto.path("generationStatus").asText().equals("READY"));
    }

    @Test
    void pythonFailureKeepsVacancyForRetry() throws Exception {
        long employer = 810006L;
        role(employer, "EMPLOYER");
        doThrow(new PythonServiceException(503, "Python unavailable"))
                .when(python).call(any(), any(), anyString());
        JsonNode vacancy = response(post("/api/v1/employer/vacancies")
                .header(header(), signed(employer)).contentType("application/json")
                .content("""
                        {"role":"JAVA_DEVELOPER","workFormat":"REMOTE",
                         "skills":[{"code":"JAVA_CORE","minLevel":2,"required":true,"weight":25}],
                         "testMode":"NONE"}
                        """));
        assertTrue(vacancy.path("matchingStatus").asText().equals("FAILED"));
        response(get("/api/v1/employer/vacancies/{id}", vacancy.path("id").asText())
                .header(header(), signed(employer)));
    }

    private JsonNode matching(JsonNode body) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (JsonNode candidate : body.path("candidates")) {
            matches.add(Map.of("candidateId", candidate.path("candidateId").asText(),
                    "eligible", true, "score", 93.5,
                    "matched", List.of(), "partial", List.of(), "missing", List.of()));
        }
        return json.tree(Map.of("vacancyId", body.path("vacancy").path("id").asText(),
                "algorithmVersion", "matching-v1", "matches", matches));
    }

    private JsonNode generatedTest(JsonNode body) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            if (i == 7) {
                questions.add(Map.of("questionId", "q7", "type", "FREE_TEXT",
                        "competency", "CODE_QUALITY", "text", "Suggest an improvement",
                        "rubric", Map.of("maxScore", 15, "criteria", List.of("clarity"))));
            } else {
                questions.add(Map.of("questionId", "q" + i, "type", "SINGLE_CHOICE",
                        "competency", "JAVA_CORE", "text", "Question " + i,
                        "options", List.of("List", "Set"), "correctAnswer", Map.of("optionIndex", 1),
                        "maxScore", 10));
            }
        }
        return json.tree(Map.of("testId", "tmp-test-77", "generationVersion", "testgen-v1",
                "estimatedDurationMinutes", 7, "questions", questions));
    }

    private JsonNode score(JsonNode body) {
        return json.tree(Map.of("assignmentId", body.path("assignmentId").asText(),
                "scoringVersion", "assessment-v1", "totalScore", 100,
                "questions", List.of(Map.of("questionId", "q1", "score", 10,
                        "maxScore", 10, "explanation", "Correct")),
                "breakdown", List.of(Map.of("competency", "JAVA_CORE", "score", 10,
                        "maxScore", 10, "percent", 100)), "summary", "Strong Java basics"));
    }

    private JsonNode rank(JsonNode body) {
        List<Map<String, Object>> finals = new ArrayList<>();
        List<Map<String, Object>> waiting = new ArrayList<>();
        for (JsonNode candidate : body.path("candidates")) {
            String id = candidate.path("candidateId").asText();
            if (candidate.path("testState").asText().equals("WAITING")) {
                waiting.add(Map.of("candidateId", id, "state", "WAITING_TEST",
                        "matchingScore", 93.5));
            } else {
                Map<String, Object> components = new LinkedHashMap<>();
                components.put("matching", 93.5);
                components.put("assessment", candidate.path("testScore").isNumber()
                        ? candidate.path("testScore").doubleValue() : null);
                finals.add(Map.of("candidateId", id, "rank", finals.size() + 1,
                        "finalScore", candidate.path("testScore").isNumber() ? 96.1 : 93.5,
                        "state", candidate.path("testState").asText().equals("NOT_REQUIRED")
                                ? "NO_TEST" : "FINAL", "components", components));
            }
        }
        return json.tree(Map.of("vacancyId", body.path("vacancyId").asText(),
                "rankingVersion", "ranking-v1", "final", finals, "waiting", waiting));
    }

    private void role(long maxId, String role) throws Exception {
        mvc.perform(put("/api/v1/me/role").header(header(), signed(maxId))
                .contentType("application/json").content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String content = mvc.perform(request).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.read(content);
    }

    private static String header() { return MaxAuthenticationFilter.INIT_DATA_HEADER; }
    private static String signed(long maxId) {
        return MaxInitDataFixtures.signed(maxId, Instant.now());
    }
}
