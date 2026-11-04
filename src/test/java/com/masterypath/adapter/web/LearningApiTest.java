package com.masterypath.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.LearningProgress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

/**
 * 路径 / 地图 / 进度 / 事件 / 题库端点（{@code TC-I1}…{@code TC-I4}）。
 *
 * <p>对应参考实现 {@code tests/test_api.py} 与 {@code tests/test_f0_api.py} 的 REST 部分。
 */
class LearningApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    private ApiTestSupport api;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport();
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    // -- 路径 ----------------------------------------------------------------

    @Test
    void createPathReturns201AndState() throws Exception {
        MvcResult result = api.mvc.perform(post("/learning/paths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path_id\":\"p\",\"goal\":\"learn X\",\"name\":\"X\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        Map<String, Object> state = json(result);
        assertThat(state).containsEntry("status", "active")
                .containsEntry("path_id", "p")
                .containsEntry("goal", "learn X");
    }

    @Test
    void createPathWithoutIdIs422() throws Exception {
        assertThat(api.mvc.perform(post("/learning/paths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"x\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void createDuplicatePathIs409() throws Exception {
        api.service.createPath("p", "g", "n");
        assertThat(api.mvc.perform(post("/learning/paths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path_id\":\"p\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void getUnknownPathIs404() throws Exception {
        assertThat(api.mvc.perform(get("/learning/paths/nope"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void deletePathReturns204() throws Exception {
        api.service.createPath("p", "g", "n");
        assertThat(api.mvc.perform(delete("/learning/paths/p"))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(api.mvc.perform(get("/learning/paths/p"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void renamePath() throws Exception {
        api.service.createPath("p", "g", "n");
        Map<String, Object> body = json(api.mvc.perform(patch("/learning/paths/p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重命名\"}"))
                .andReturn());
        assertThat(body).containsEntry("name", "重命名");
        assertThat(api.store.load("p").getName()).isEqualTo("重命名");
    }

    @Test
    void patchWithoutNameReturnsState() throws Exception {
        api.service.createPath("p", "g", "n");
        Map<String, Object> body = json(api.mvc.perform(patch("/learning/paths/p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn());
        assertThat(body).containsEntry("status", "active");
    }

    @Test
    void redoClearsLearningStateButKeepsMap() throws Exception {
        api.buildOutline("p");
        api.service.overrideMastery("p", "k1", "");
        assertThat(api.store.load("p").getLearnerMasteryOverrides()).isNotEmpty();

        Map<String, Object> body = json(api.mvc.perform(post("/learning/paths/p/redo")).andReturn());

        assertThat(body).containsEntry("status", "reset");
        LearningProgress progress = api.store.load("p");
        assertThat(progress.getLearnerMasteryOverrides()).isEmpty();
        assertThat(progress.getModules()).hasSize(1);
    }

    @Test
    void redoUnknownPathIs404() throws Exception {
        assertThat(api.mvc.perform(post("/learning/paths/nope/redo"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void reviewSettingsClampsToRange() throws Exception {
        api.service.createPath("p", "g", "n");
        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/review-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desired_retention\":0.95}"))
                .andReturn());
        assertThat(body).containsEntry("desired_retention", 0.95)
                .containsEntry("scope", "path");
        assertThat(api.store.load("p").getDesiredRetention()).isEqualTo(0.95);

        Map<String, Object> clamped = json(api.mvc.perform(put("/learning/paths/p/review-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desired_retention\":0.1}"))
                .andReturn());
        assertThat(clamped).containsEntry("desired_retention", 0.7);
    }

    @Test
    void listPathsSkipsOwnedEmptyScratchButKeepsPlainEmptyPath() throws Exception {
        api.service.createPath("plain", "g", "n");
        api.service.ensureScratchPath("s1");

        List<Map<String, Object>> rows = jsonList(api.mvc.perform(get("/learning/paths")).andReturn());

        assertThat(rows).extracting(row -> row.get("path_id")).containsExactly("plain");
    }

    // -- 地图 ----------------------------------------------------------------

    @Test
    void mapWriteRequiresOutlineMode() throws Exception {
        api.service.createPath("p", "g", "n");
        api.service.setSessionMode("p", "s1", "review");

        assertThat(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"name\":\"M\",\"knowledge_points\":"
                                + "[{\"name\":\"obj\",\"type\":\"memory\"}]}]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void mapWriteWithoutRecordedModeIsAllowed() throws Exception {
        api.service.createPath("p", "g", "n");

        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"name\":\"M\",\"knowledge_points\":"
                                + "[{\"name\":\"obj\",\"type\":\"memory\"}]}]}"))
                .andReturn());

        assertThat(body).containsEntry("status", "built");
        assertThat(body).containsKey("version");
    }

    @Test
    void mapWriteInOutlineModeBuilds() throws Exception {
        api.service.createPath("p", "g", "n");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"name\":\"M\",\"knowledge_points\":"
                                + "[{\"name\":\"obj\",\"type\":\"memory\"}]}]}"))
                .andReturn());

        assertThat(body).containsEntry("status", "built");
    }

    @Test
    void mapWriteWithOpsRevises() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> body = json(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ops\":[{\"op\":\"add_kp\",\"module_id\":\"m1\","
                                + "\"name\":\"second fact\",\"type\":\"memory\"}]}"))
                .andReturn());

        assertThat(body).containsEntry("status", "revised");
        assertThat((List<?>) body.get("applied")).isNotEmpty();
    }

    @Test
    void mapWriteWithInvalidPayloadIs422() throws Exception {
        api.service.createPath("p", "g", "n");
        assertThat(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void mapWriteWithoutSessionIdIs422() throws Exception {
        api.service.createPath("p", "g", "n");
        assertThat(api.mvc.perform(put("/learning/paths/p/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[{\"name\":\"M\",\"knowledge_points\":"
                                + "[{\"name\":\"obj\",\"type\":\"memory\"}]}]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void rebuildRequiresConfirm() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        assertThat(api.mvc.perform(post("/learning/paths/p/rebuild")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void rebuildWithConfirmReplacesMap() throws Exception {
        api.buildOutline("p");
        api.service.setSessionMode("p", "s1", "outline");

        Map<String, Object> body = json(api.mvc.perform(post("/learning/paths/p/rebuild")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true,\"modules\":[{\"name\":\"M2\","
                                + "\"knowledge_points\":[{\"name\":\"other\",\"type\":\"memory\"}]}]}"))
                .andReturn());

        assertThat(body).containsEntry("status", "built").containsEntry("rebuilt", true);
    }

    @Test
    void overrideUnknownObjectiveIs412() throws Exception {
        api.buildOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/objectives/nope/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(412);
    }

    @Test
    void overrideKnownObjectiveSucceeds() throws Exception {
        api.buildOutline("p");
        Map<String, Object> body = json(api.mvc.perform(post("/learning/paths/p/objectives/k1/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"already know it\"}"))
                .andReturn());
        assertThat(body).containsEntry("knowledge_point_id", "k1")
                .containsEntry("source", "learner");
    }

    @Test
    void resolveUnknownGapIs422() throws Exception {
        api.buildOutline("p");
        assertThat(api.mvc.perform(post("/learning/paths/p/gaps/nope/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"accept\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void resolveGapReturnsResolution() throws Exception {
        api.buildOutline("p");
        String gapId = String.valueOf(api.store.load("p").getSourceGaps().get(0).getId());

        Map<String, Object> body = json(api.mvc.perform(post("/learning/paths/p/gaps/" + gapId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"accept\"}"))
                .andReturn());

        assertThat(body).containsEntry("gap_id", gapId).containsEntry("resolution", "accepted");
    }

    // -- 进度 / 事件 / 题库 ---------------------------------------------------

    @Test
    void masteryReturnsMapOnly() throws Exception {
        api.buildOutline("p");
        Map<String, Object> body = json(api.mvc.perform(get("/learning/paths/p/mastery")).andReturn());
        assertThat(body).containsKey("k1");
        assertThat(body).doesNotContainKey("status");
    }

    @Test
    void masteryUnknownPathIs404() throws Exception {
        assertThat(api.mvc.perform(get("/learning/paths/nope/mastery"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void eventsSinceFiltersByRevision() throws Exception {
        api.buildOutline("p");

        List<Map<String, Object>> all = jsonList(
                api.mvc.perform(get("/learning/paths/p/events").param("since", "0")).andReturn());
        assertThat(all).extracting(row -> row.get("event_type")).contains("outline.built");

        List<Map<String, Object>> none = jsonList(
                api.mvc.perform(get("/learning/paths/p/events").param("since", "999")).andReturn());
        assertThat(none).isEmpty();
    }

    @Test
    void eventsForUnknownPathIsEmptyList() throws Exception {
        assertThat(jsonList(api.mvc.perform(get("/learning/paths/nope/events")).andReturn())).isEmpty();
    }

    /** {@code TC-I5}：失败的写入不产生事件（事务回滚，{@code INV-51}）。 */
    @Test
    void failedMapWriteEmitsNoEvent() throws Exception {
        api.service.createPath("p", "g", "n");
        int before = jsonList(api.mvc.perform(get("/learning/paths/p/events")).andReturn()).size();

        assertThat(api.mvc.perform(put("/learning/paths/p/map")
                        .param("session_id", "s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\":[]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);

        int after = jsonList(api.mvc.perform(get("/learning/paths/p/events")).andReturn()).size();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void reviewQueueReturnsDueReviews() throws Exception {
        api.buildOutline("p");
        assertThat(jsonList(api.mvc.perform(get("/learning/paths/p/review-queue")).andReturn())).isEmpty();
    }

    @Test
    void tagQuestionReplacesTags() throws Exception {
        api.buildOutline("p");
        api.service.registerQuestion("p", pendingQuestion(), "s1", "");
        api.service.grade("p", "q1", "fact one", 0, false, "s1", "");

        List<Map<String, Object>> entries = jsonList(
                api.mvc.perform(get("/learning/paths/p/questions")).andReturn());
        assertThat(entries).hasSize(1);
        String entryId = String.valueOf(entries.get(0).get("entry_id"));

        Map<String, Object> tagged = json(api.mvc.perform(post("/learning/questions/" + entryId + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"a\",\"b\",\"a\"]}"))
                .andReturn());
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) tagged.get("tags");
        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void tagUnknownEntryIs404() throws Exception {
        assertThat(api.mvc.perform(post("/learning/questions/nope/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void globalQuestionsAggregatesAcrossPaths() throws Exception {
        api.buildOutline("p");
        api.service.registerQuestion("p", pendingQuestion(), "s1", "");
        api.service.grade("p", "q1", "fact one", 0, false, "s1", "");

        List<Map<String, Object>> rows = jsonList(api.mvc.perform(get("/questions")).andReturn());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("path_name", "X");
    }

    @Test
    void proposalsListAndResolve() throws Exception {
        api.buildOutline("p");
        Map<String, Object> proposed = api.service.proposeObjective(
                "p", "extra idea", "concept", "found while studying", "m1",
                List.of(), "tutor", "s1", "");
        String proposalId = String.valueOf(proposed.get("proposal_id"));

        List<Map<String, Object>> proposals = jsonList(
                api.mvc.perform(get("/learning/paths/p/proposals")).andReturn());
        assertThat(proposals).hasSize(1);

        Map<String, Object> resolved = json(api.mvc.perform(
                        post("/learning/paths/p/proposals/" + proposalId + "/resolve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"action\":\"accept\"}"))
                .andReturn());
        assertThat(resolved).containsEntry("proposal_id", proposalId)
                .containsEntry("status", "accepted");
    }

    @Test
    void resolveProposalWithUnknownActionIs422() throws Exception {
        api.buildOutline("p");
        Map<String, Object> proposed = api.service.proposeObjective(
                "p", "extra idea", "concept", "", "m1", List.of(), "tutor", "s1", "");
        String proposalId = String.valueOf(proposed.get("proposal_id"));

        assertThat(api.mvc.perform(post("/learning/paths/p/proposals/" + proposalId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"banana\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void coverageUnknownPathIs404() throws Exception {
        assertThat(api.mvc.perform(get("/learning/paths/nope/coverage"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void coverageReturnsReport() throws Exception {
        api.buildOutline("p");
        Map<String, Object> body = json(api.mvc.perform(get("/learning/paths/p/coverage")).andReturn());
        assertThat(body).containsKeys("documents", "covered", "reported", "grounding_ratio", "acyclic");
    }

    // -- 辅助 -----------------------------------------------------------------

    private static com.masterypath.domain.model.PendingQuestion pendingQuestion() {
        com.masterypath.domain.model.PendingQuestion pending =
                new com.masterypath.domain.model.PendingQuestion();
        pending.setQuestionId("q1");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("what is the fact?");
        pending.setQuestionType(com.masterypath.domain.model.PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("fact one");
        return pending;
    }

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
