package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.turn.TurnEvent;
import com.masterypath.adapter.turn.TurnRequest;
import com.masterypath.adapter.turn.TurnRunner;
import com.masterypath.domain.model.JsonSupport;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /ws} 回合端点（参考实现 {@code api/app.py:555-600}）。
 *
 * <p>只做三件事：收一条 JSON、把 {@link TurnRunner} 的事件流<b>逐条</b>推送、关闭连接。
 * 消息格式固定为 {@code {"type": ..., "data": {...}}}，与 REST 的 {@code {ok, content, ...}}
 * 不同，不可混用。
 *
 * <p>参考实现用「后台线程 + 队列」避免阻塞事件循环；Spring 的 {@code WebSocketHandler}
 * 本身在独立线程池执行，因此这里同步迭代即可，不需要额外线程。
 *
 * <p><b>逐条 {@code sendMessage}</b> 是协议要求：攒批会让前端在长回合里收不到增量。
 */
@Component
public class WsTurnHandler extends TextWebSocketHandler {

    private final TurnRunner turnRunner;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    public WsTurnHandler(TurnRunner turnRunner) {
        this.turnRunner = turnRunner;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        TurnRequest request = parse(message.getPayload());
        try {
            Iterator<TurnEvent> events = turnRunner.run(request);
            while (events.hasNext()) {
                TurnEvent event = events.next();
                session.sendMessage(new TextMessage(encode(event)));
            }
        } catch (RuntimeException e) {
            // 参考实现同样兜底：报告错误，绝不静默关闭 socket。
            session.sendMessage(new TextMessage(encode(TurnEvent.error(
                    "internal_error", "The turn failed: " + e.getMessage(), false))));
        } finally {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 把客户端 JSON 解析成回合请求；缺字段一律退化成空串（参考实现 {@code str(payload.get(...) or "")}）。 */
    @SuppressWarnings("unchecked")
    private TurnRequest parse(String payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Object parsed = mapper.readValue(payload, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                body = new LinkedHashMap<>((Map<String, Object>) map);
            }
        } catch (IOException ignored) {
            // 坏 JSON 等价于空请求：回合会以空消息跑一次，与参考实现一致。
        }
        TurnRequest request = new TurnRequest();
        String capability = str(body.get("capability")).strip();
        request.setCapability(capability.isEmpty() ? TurnRequest.CAPABILITY_MASTERY : capability);
        request.setSessionId(str(body.get("session_id")));
        request.setPathId(str(body.get("path_id")));
        request.setMode(str(body.get("mode")));
        request.setTurnId(str(body.get("turn_id")));
        request.setLlmSelection(body.get("llm_selection"));
        request.setMessage(str(body.get("message")));
        request.setNumQuestions(intOf(body.get("num_questions"), 3));
        request.setDifficulty(str(body.get("difficulty")));
        request.setQuestionTypes(stringList(body.get("question_types")));
        return request;
    }

    /** 序列化成 {@code {"type": ..., "data": {...}}}。 */
    private String encode(TurnEvent event) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", event.type());
        frame.put("data", event.data());
        try {
            return mapper.writeValueAsString(frame);
        } catch (IOException e) {
            return "{\"type\":\"error\",\"data\":{\"error_code\":\"internal_error\","
                    + "\"message\":\"The turn failed: cannot encode event\",\"retryable\":false}}";
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static java.util.List<String> stringList(Object value) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }
}
