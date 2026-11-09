package com.masterypath.adapter.turn;

import com.masterypath.adapter.llm.ClientBuilder;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.domain.mode.SessionMode;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.QuizSet;
import com.masterypath.domain.model.TimeSupport;
import com.masterypath.domain.projection.QuizSetProjector;
import com.masterypath.domain.quizset.QuizSetSpec;
import com.masterypath.service.LearningService;
import com.masterypath.service.QuizSetService;
import com.masterypath.storage.LearningStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * {@code deep_question} 回合：生成一套练习题并结束。
 *
 * <p>事件顺序固定为 {@code quiz_plan} → {@code quiz_question} × N → {@code quiz_set} →
 * {@code turn_end}。生成失败只发 {@code error} + {@code turn_end}，零落库。
 */
final class QuizSetTurn implements Iterator<TurnEvent> {

    private final TurnRequest request;
    private final LearningService learningService;
    private final LearningStore store;
    private final ClientBuilder clientBuilder;
    private final Deque<TurnEvent> pending = new ArrayDeque<>();

    private boolean initialized;

    QuizSetTurn(TurnRequest request, LearningService learningService,
                LearningStore store, ClientBuilder clientBuilder) {
        this.request = request;
        this.learningService = learningService;
        this.store = store;
        this.clientBuilder = clientBuilder;
    }

    @Override
    public boolean hasNext() {
        if (!initialized) {
            initialized = true;
            initialize();
        }
        return !pending.isEmpty();
    }

    @Override
    public TurnEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException("quiz set turn already ended");
        }
        return pending.removeFirst();
    }

    private void initialize() {
        String pathId = resolvePathId();
        if (pathId.isEmpty()) {
            fail("no mastery path is active");
            return;
        }
        LearningProgress progress = store.load(pathId);
        if (progress == null) {
            fail("no mastery path is active");
            return;
        }
        QuizSetSpec spec = QuizSetSpec.normalize(
                request.message(), request.numQuestions(),
                request.difficulty(), request.questionTypes());
        String setId = UUID.randomUUID().toString().replace("-", "");
        try {
            LlmClient client = clientBuilder.build(request.llmSelection(), request.sessionId());
            List<QuizItem> items = new QuizSetGenerator(client).generate(spec, setId);

            QuizSet set = new QuizSet();
            set.setSetId(setId);
            set.setPathId(pathId);
            set.setSessionId(request.sessionId());
            set.setTurnId(request.turnId());
            set.setTitle("Practice: " + spec.topic());
            set.setTopic(spec.topic());
            set.setStatus(QuizSet.STATUS_OPEN);
            set.setQuestionCount(items.size());
            set.setCreatedAt(TimeSupport.nowSeconds());
            new QuizSetService(store).save(set, items);

            Map<String, Object> publicSet = QuizSetProjector.publicSet(set, items, false);
            try {
                learningService.recordQuizSetTurn(pathId, request.sessionId(),
                        request.message(), "Generated " + items.size()
                                + " practice questions.", sessionMode(), request.turnId(),
                        publicSet);
            } catch (RuntimeException ignored) {
                // 落库是 best-effort：题卡已经可消费，不能因为会话写入失败而回滚题卷。
            }

            pending.add(new TurnEvent("quiz_plan", planPayload(set, items, pathId)));
            for (QuizItem item : items) {
                Map<String, Object> card = new LinkedHashMap<>(
                        QuizSetProjector.publicItem(item));
                card.put("set_id", setId);
                pending.add(new TurnEvent("quiz_question", card));
            }
            pending.add(new TurnEvent("quiz_set", publicSet));
            pending.add(new TurnEvent(TurnEvent.TURN_END, Map.of("reason", "quiz_set")));
        } catch (QuizSetGenerator.QuizGenerationException e) {
            fail(e.getMessage());
        } catch (LlmError e) {
            fail(e.code(), e.getMessage(), e.retryable());
        } catch (RuntimeException e) {
            fail("internal_error", "The turn failed: " + e.getMessage(), false);
        }
    }

    private Map<String, Object> planPayload(QuizSet set, List<QuizItem> items, String pathId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("set_id", set.getSetId());
        payload.put("path_id", pathId);
        payload.put("topic", set.getTopic());
        payload.put("count", items.size());
        List<Map<String, Object>> templates = new ArrayList<>();
        for (QuizItem item : items) {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("question_type", item.getQuestion().getQuestionType().value());
            template.put("difficulty", item.getQuestion().getDifficulty());
            templates.add(template);
        }
        payload.put("templates", templates);
        return payload;
    }

    private String resolvePathId() {
        if (!request.sessionId().isEmpty()) {
            Map<String, Object> session = store.getSession(request.sessionId());
            if (session != null) {
                String bound = String.valueOf(session.getOrDefault("path_id", "")).strip();
                if (!bound.isEmpty()) {
                    return bound;
                }
            }
        }
        return request.pathId().strip();
    }

    private String sessionMode() {
        Map<String, Object> session = request.sessionId().isEmpty()
                ? null : store.getSession(request.sessionId());
        Object mode = session == null ? null : session.get("mode");
        SessionMode normalized = SessionMode.enforced(mode);
        if (normalized == null) {
            normalized = SessionMode.enforced(request.mode());
        }
        return normalized == null ? "study" : normalized.value();
    }

    private void fail(String message) {
        fail("quiz_generation_failed", message, false);
    }

    private void fail(String errorCode, String message, boolean retryable) {
        pending.add(TurnEvent.error(errorCode, message, retryable));
        pending.add(new TurnEvent(TurnEvent.TURN_END, Map.of("reason", "error")));
    }
}
