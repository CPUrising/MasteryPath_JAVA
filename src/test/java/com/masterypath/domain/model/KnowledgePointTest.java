package com.masterypath.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** TC-A1…A4：模型校验与历史兼容（spec 03 §A）。 */
class KnowledgePointTest {

    private final ObjectMapper mapper = JsonSupport.objectMapper();

    /** TC-A1：名称去空白后少于 2 字符 → 拒绝。 */
    @Test
    void nameTooShortRejectedOnConstruction() {
        assertThatThrownBy(() -> new KnowledgePoint("k", "a", KnowledgeType.MEMORY, "m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 characters");
    }

    /** TC-A1：JSON 反序列化路径同样拒绝（校验不能只在构造器里）。 */
    @Test
    void nameTooShortRejectedOnDeserialization() {
        String json = "{\"id\":\"k\",\"name\":\" a \",\"type\":\"memory\",\"module_id\":\"m\"}";
        assertThatThrownBy(() -> mapper.readValue(json, KnowledgePoint.class))
                .isInstanceOf(Exception.class);
    }

    /** TC-A2：未知类型 → 拒绝，不静默映射。 */
    @Test
    void unknownTypeRejected() {
        String json = "{\"id\":\"k\",\"name\":\"ab\",\"type\":\"foo\",\"module_id\":\"m\"}";
        assertThatThrownBy(() -> mapper.readValue(json, KnowledgePoint.class))
                .isInstanceOf(Exception.class);
    }

    /** TC-A3：历史中文类型 → 映射为 concept。 */
    @Test
    void legacyChineseTypeMapped() throws Exception {
        String json = "{\"id\":\"k\",\"name\":\"ab\",\"type\":\"概念型\",\"module_id\":\"m\"}";
        KnowledgePoint kp = mapper.readValue(json, KnowledgePoint.class);
        assertThat(kp.getType()).isEqualTo(KnowledgeType.CONCEPT);
    }

    /** TC-A4：未知字段被忽略，其余字段正常解析。 */
    @Test
    void unknownFieldsIgnored() throws Exception {
        String json = "{\"id\":\"k\",\"name\":\"ab\",\"type\":\"concept\",\"module_id\":\"m\",\"whatever\":123}";
        KnowledgePoint kp = mapper.readValue(json, KnowledgePoint.class);
        assertThat(kp.getName()).isEqualTo("ab");
        assertThat(kp.getType()).isEqualTo(KnowledgeType.CONCEPT);
    }

    /** 派生值：有来源即接地（INV-24），且不进入序列化。 */
    @Test
    void groundingIsDerived() throws Exception {
        KnowledgePoint kp = new KnowledgePoint("k", "ab", KnowledgeType.CONCEPT, "m");
        assertThat(kp.grounding()).isEqualTo("ungrounded");

        kp.setSourceIds(java.util.List.of("s1"));
        assertThat(kp.grounding()).isEqualTo("grounded");

        assertThat(mapper.writeValueAsString(kp)).doesNotContain("grounding");
    }
}
