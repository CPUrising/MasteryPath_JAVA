package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.ChatService;
import com.masterypath.storage.ChatStore;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 普通聊天的 REST + SSE 端点（参考实现 {@code api/chat_routes.py}）。
 *
 * <p>五个会话端点 + 一个回合端点。回合用 SSE：每帧
 * {@code data: {"type": ..., "data": {...}}\n\n}，与 WS 帧同构。
 *
 * <p>未知会话统一 404（参考实现 {@code catch UnknownSession → HTTPException(404)}）。
 */
@RestController
@RequestMapping("/chat/sessions")
public class ChatController {

    private final ChatService service;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.listSessions();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        List<String> kbIds = new ArrayList<>();
        if (body.get("kb_ids") instanceof List<?> list) {
            for (Object item : list) {
                kbIds.add(ApiSupport.str(item));
            }
        }
        Map<String, Object> created = service.createSession(
                ApiSupport.str(body.get("title")),
                ApiSupport.str(body.get("model_id")),
                kbIds);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{sessionId}")
    public Map<String, Object> detail(@PathVariable String sessionId) {
        try {
            return service.sessionDetail(sessionId);
        } catch (ChatStore.UnknownSession e) {
            throw new ApiException(404, sessionId);
        }
    }

    @PatchMapping("/{sessionId}")
    public Map<String, Object> update(@PathVariable String sessionId,
                                      @RequestBody(required = false) Map<String, Object> payload) {
        try {
            return service.updateSession(sessionId, payload == null ? Map.of() : payload);
        } catch (ChatStore.UnknownSession e) {
            throw new ApiException(404, sessionId);
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        try {
            service.deleteSession(sessionId);
        } catch (ChatStore.UnknownSession e) {
            throw new ApiException(404, sessionId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 撤回一条消息：删除它及其之后的所有消息。
     *
     * <p>前端「撤回我的消息」使用；删除后对话回到发送前的状态。
     */
    @DeleteMapping("/{sessionId}/messages/{messageId}")
    public ResponseEntity<Void> withdraw(@PathVariable String sessionId,
                                         @PathVariable long messageId) {
        try {
            service.withdrawFrom(sessionId, messageId);
        } catch (ChatStore.UnknownSession e) {
            throw new ApiException(404, sessionId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 一个聊天回合的 SSE 流。
     *
     * <p>响应头带 {@code X-Accel-Buffering: no}：反向代理默认会缓冲 SSE，前端就收不到增量。
     */
    @PostMapping(value = "/{sessionId}/turns", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> turn(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        List<Map<String, Object>> attachments = new ArrayList<>();
        if (body.get("attachments") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> attachment = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        attachment.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    attachments.add(attachment);
                }
            }
        }
        Iterator<Map<String, Object>> events =
                service.streamTurn(sessionId, ApiSupport.str(body.get("message")), attachments);

        StreamingResponseBody stream = output -> {
            while (events.hasNext()) {
                Map<String, Object> event = events.next();
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("type", event.get("type"));
                Map<String, Object> data = new LinkedHashMap<>(event);
                data.remove("type");
                frame.put("data", data);
                write(output, mapper.writeValueAsString(frame) + "\n\n");
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.setCacheControl("no-cache");
        headers.setConnection("keep-alive");
        headers.add("X-Accel-Buffering", "no");
        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 逐帧写出并立即 flush（攒批会让前端在长回合里收不到增量）。 */
    private static void write(OutputStream output, String frame) throws IOException {
        output.write(("data: " + frame).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
