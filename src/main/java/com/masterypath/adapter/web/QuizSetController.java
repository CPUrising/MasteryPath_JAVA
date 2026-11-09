package com.masterypath.adapter.web;

import com.masterypath.domain.model.QuizSet;
import com.masterypath.domain.projection.QuizSetProjector;
import com.masterypath.service.QuizSetService;
import com.masterypath.storage.PreconditionFailedException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 练习题卷 REST 端点（{@code 0033}）。
 *
 * <p>提交前只返回公开投影；提交成功后才释放答案与解析。练习卷不影响掌握闭环。
 */
@RestController
@RequestMapping("/learning/paths/{pathId}/quiz-sets")
public class QuizSetController {

    private final QuizSetService service;

    public QuizSetController(QuizSetService service) {
        this.service = service;
    }

    @GetMapping("/{setId}")
    public Map<String, Object> get(@PathVariable String pathId, @PathVariable String setId) {
        boolean includeAnswers = isSubmitted(pathId, setId);
        Map<String, Object> view = service.view(pathId, setId, includeAnswers);
        if (view == null) {
            throw new ApiException(404, "unknown quiz set");
        }
        return view;
    }

    @PostMapping("/{setId}/submit")
    public Map<String, Object> submit(@PathVariable String pathId, @PathVariable String setId,
                                      @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Object rawAnswers = body.get("answers");
        if (!(rawAnswers instanceof List<?> list) || list.isEmpty()) {
            throw new ApiException(422, "answers are required");
        }
        Map<String, String> answers = new LinkedHashMap<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                answers.put(ApiSupport.str(map.get("question_id")),
                        ApiSupport.str(map.get("answer")));
            }
        }
        QuizSetService.SubmitOutcome outcome;
        try {
            outcome = service.submit(pathId, setId, answers);
        } catch (PreconditionFailedException e) {
            throw new ApiException(404, String.valueOf(e.getMessage()));
        }
        if ("ungraded".equals(outcome.status())) {
            Map<String, Object> view = QuizSetProjector.publicSet(
                    outcome.set(), outcome.items(), false);
            view.put("status", "ungraded");
            view.put("ungraded_reasons", outcome.ungradedReasons());
            return view;
        }
        return QuizSetProjector.publicSet(outcome.set(), outcome.items(), true);
    }

    private boolean isSubmitted(String pathId, String setId) {
        QuizSet set = service.get(setId);
        return set != null && set.getPathId().equals(pathId)
                && QuizSet.STATUS_SUBMITTED.equals(set.getStatus());
    }
}
