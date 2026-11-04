package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

/**
 * 领域模型与 JSON 之间的统一映射配置。
 *
 * <p>与参考实现对齐的三条硬性设定（见 {@code 0002-domain-model/design.md}）：
 * <ol>
 *   <li>字段名用 {@code snake_case}：旧库 JSON 与前端契约都用 {@code path_id} 这类名字。</li>
 *   <li>忽略未知字段：等价于 Pydantic 的 {@code extra="ignore"}，保证旧数据可加载（{@code TC-A4}）。</li>
 *   <li>空值照常写出：等价于 Pydantic {@code model_dump_json()} 的默认行为；
 *       若省略 {@code null}，参考实现读取 Java 写出的数据时会因缺字段而失败。</li>
 * </ol>
 *
 * <p><b>刻意不开启字段级可见性</b>：反序列化必须经过 setter，否则 {@code KnowledgePoint}
 * 的名称长度校验（{@code TC-A1}）会被绕过。
 *
 * <p>本类位于 {@code domain.model}，只依赖 Jackson 注解与 {@code java.*}，不依赖 Spring。
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /** 创建一个与参考实现语义一致的 {@link ObjectMapper}。 */
    public static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // 只通过 public getter/setter 访问，保证校验逻辑在反序列化时生效。
        mapper.setVisibility(VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withSetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return mapper;
    }
}
