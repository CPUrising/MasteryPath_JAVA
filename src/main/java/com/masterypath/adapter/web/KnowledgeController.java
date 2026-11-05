package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.KnowledgeService;
import com.masterypath.storage.KnowledgeStore;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库 REST 端点（参考实现 {@code api/knowledge_routes.py}）。
 *
 * <p>错误码分工：
 * <ul>
 *   <li><b>404</b> 未知知识库（{@link KnowledgeStore.UnknownKnowledgeBase}）。</li>
 *   <li><b>422</b> 参数非法（{@link KnowledgeStore.KnowledgeError}）：名字为空、
 *       分块配置越界、路径穿越、不支持的文件类型。</li>
 * </ul>
 */
@RestController
@RequestMapping("/knowledge-bases")
public class KnowledgeController {

    private final KnowledgeService service;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        Object indexConfig = body.get("index_config");
        if (!(indexConfig instanceof Map<?, ?>) && (body.get("chunk_size") != null
                || body.get("chunk_overlap") != null)) {
            Map<String, Object> fromFields = new LinkedHashMap<>();
            fromFields.put("chunk_size", body.get("chunk_size"));
            fromFields.put("chunk_overlap", body.get("chunk_overlap"));
            indexConfig = fromFields;
        }
        try {
            Map<String, Object> created = service.create(
                    ApiSupport.str(body.get("name")),
                    ApiSupport.bool(body.get("mode")) ? ApiSupport.str(body.get("mode")) : "import",
                    ApiSupport.str(body.get("embedding_model_id")),
                    ApiSupport.str(body.get("description")),
                    indexConfig instanceof Map<?, ?> map ? castMap(map) : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        } catch (KnowledgeStore.KnowledgeError e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    @GetMapping("/{kbId}")
    public Map<String, Object> get(@PathVariable String kbId) {
        try {
            return service.get(kbId);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    @PatchMapping("/{kbId}")
    public Map<String, Object> update(@PathVariable String kbId,
                                      @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> fields = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        if (!fields.containsKey("index_config")
                && (fields.get("chunk_size") != null || fields.get("chunk_overlap") != null)) {
            Map<String, Object> indexConfig = new LinkedHashMap<>();
            indexConfig.put("chunk_size", fields.remove("chunk_size"));
            indexConfig.put("chunk_overlap", fields.remove("chunk_overlap"));
            fields.put("index_config", indexConfig);
        }
        try {
            return service.update(kbId, fields);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        } catch (KnowledgeStore.KnowledgeError e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    @DeleteMapping("/{kbId}")
    public ResponseEntity<Void> delete(@PathVariable String kbId) {
        try {
            service.delete(kbId);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{kbId}/files")
    public List<Map<String, Object>> files(@PathVariable String kbId) {
        try {
            return service.files(kbId);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    @DeleteMapping("/{kbId}/files/{*relPath}")
    public ResponseEntity<Void> deleteFile(@PathVariable String kbId,
                                           @PathVariable String relPath) {
        try {
            service.removeFile(kbId, stripLeadingSlash(relPath));
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        } catch (KnowledgeStore.KnowledgeError e) {
            throw new ApiException(422, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{kbId}/folders")
    public Map<String, Object> addFolder(@PathVariable String kbId,
                                         @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String folder = ApiSupport.str(body.get("folder"));
        if (folder.isEmpty()) {
            throw new ApiException(422, "folder required");
        }
        String mode = body.get("mode") == null ? "import" : ApiSupport.str(body.get("mode"));
        try {
            if ("link".equals(mode)) {
                return service.addLinkedFolder(kbId, folder, true);
            }
            return service.importFolder(kbId, folder, true);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        } catch (KnowledgeStore.KnowledgeError e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    @DeleteMapping("/{kbId}/linked-folders/{folderId}")
    public Map<String, Object> removeFolder(@PathVariable String kbId,
                                            @PathVariable String folderId) {
        try {
            return service.removeLinkedFolder(kbId, folderId);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    @PostMapping("/{kbId}/reindex")
    public Map<String, Object> reindex(@PathVariable String kbId) {
        try {
            return service.reindex(kbId, true);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    @GetMapping("/{kbId}/progress")
    public Map<String, Object> progress(@PathVariable String kbId) {
        try {
            return service.progress(kbId);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    /**
     * 上传文件。
     *
     * <p>{@code paths} 是 JSON 数组（与 {@code files} 一一对应）；解析失败退化为空列表，
     * 此时用原始文件名。路径穿越 → 422。
     */
    @PostMapping(value = "/{kbId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable String kbId,
                                      @RequestParam("files") List<MultipartFile> files,
                                      @RequestParam(value = "paths", defaultValue = "") String paths,
                                      @RequestParam(value = "conflict", defaultValue = "overwrite")
                                      String conflict) {
        List<Object> relative = new ArrayList<>();
        if (!paths.isEmpty()) {
            try {
                Object parsed = mapper.readValue(paths, List.class);
                if (parsed instanceof List<?> list) {
                    relative = new ArrayList<>(list);
                }
            } catch (IOException ignored) {
                // 坏 JSON 等价于空列表（参考实现同样退化为文件名）。
            }
        }
        List<KnowledgeStore.Upload> uploads = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String rel = index < relative.size() && ApiSupport.bool(relative.get(index))
                    ? ApiSupport.str(relative.get(index))
                    : (file.getOriginalFilename() == null
                    ? "upload-" + index + ".txt" : file.getOriginalFilename());
            byte[] data;
            try {
                data = file.getBytes();
            } catch (IOException e) {
                throw new ApiException(422, "cannot read uploaded file: " + rel);
            }
            uploads.add(new KnowledgeStore.Upload(rel, data));
        }
        try {
            return service.upload(kbId, uploads,
                    conflict.isEmpty() ? "overwrite" : conflict, true);
        } catch (KnowledgeStore.UnknownKnowledgeBase e) {
            throw new ApiException(404, e.getMessage());
        } catch (KnowledgeStore.KnowledgeError e) {
            throw new ApiException(422, e.getMessage());
        }
    }

    // -- 辅助 -----------------------------------------------------------------

    /** {@code /files/{*relPath}} 捕获的前导斜杠要去掉。 */
    private static String stripLeadingSlash(String value) {
        return value == null ? "" : value.replaceFirst("^/", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return new LinkedHashMap<>((Map<String, Object>) map);
    }
}
