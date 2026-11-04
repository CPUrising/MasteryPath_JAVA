package com.masterypath.service;

import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路径生命周期：创建、重复创建、改名、删除。
 */
class PathLifecycleTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("创建路径：写入 goal 与 name，发一条 path.created")
    void createPathEmitsEvent() {
        LearningProgress created = service.createPath("p", "learn X", "New");

        assertThat(created.getGoal()).isEqualTo("learn X");
        assertThat(created.getName()).isEqualTo("New");

        List<Map<String, Object>> events = store.eventsSince("p", 0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("event_type")).isEqualTo("path.created");
        assertThat(events.get(0).get("payload")).asString().contains("\"name\":\"New\"");
    }

    @Test
    @DisplayName("重复创建被拒绝，文案逐字")
    void duplicateCreateRejected() {
        service.createPath("p", "learn X", "New");

        assertThatThrownBy(() -> service.createPath("p", "again", "Again"))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessage("path already exists: p");
    }

    @Test
    @DisplayName("重复创建不产生额外事件")
    void duplicateCreateEmitsNothing() {
        service.createPath("p", "learn X", "New");

        assertThatThrownBy(() -> service.createPath("p", "again", "Again"))
                .isInstanceOf(PreconditionFailedException.class);

        assertThat(store.eventsSince("p", 0)).hasSize(1);
    }

    @Test
    @DisplayName("创建时 goal / name 为 null 落为空串")
    void nullFieldsBecomeEmpty() {
        LearningProgress created = service.createPath("p", null, null);

        assertThat(created.getGoal()).isEmpty();
        assertThat(created.getName()).isEmpty();
    }

    @Test
    @DisplayName("改名：写入并发出 path.renamed")
    void renamePersists() {
        service.createPath("p", "learn X", "Old");

        Map<String, Object> result = service.rename("p", "重命名");

        assertThat(result).containsEntry("status", "renamed").containsEntry("name", "重命名");
        assertThat(store.load("p").getName()).isEqualTo("重命名");
        assertThat(store.eventsSince("p", 0))
                .extracting(event -> event.get("event_type"))
                .contains("path.renamed");
    }

    @Test
    @DisplayName("改名不校验空名（与参考实现一致）")
    void renameAcceptsEmptyName() {
        service.createPath("p", "learn X", "Old");

        service.rename("p", "");

        assertThat(store.load("p").getName()).isEmpty();
    }

    @Test
    @DisplayName("改名保留 goal（TC-C17 的同类保护）")
    void renameKeepsGoal() {
        service.createPath("p", "learn X", "Old");

        service.rename("p", "New");

        assertThat(store.load("p").getGoal()).isEqualTo("learn X");
    }

    @Test
    @DisplayName("对不存在的路径改名抛前置条件异常")
    void renameMissingPathRejected() {
        assertThatThrownBy(() -> service.rename("nope", "x"))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    @DisplayName("删除已存在路径返回 true，之后读取为 null")
    void deleteExistingPath() {
        service.createPath("p", "learn X", "New");

        assertThat(service.delete("p")).isTrue();
        assertThat(store.load("p")).isNull();
    }

    @Test
    @DisplayName("删除不存在的路径返回 false，不抛错")
    void deleteMissingPathReturnsFalse() {
        assertThat(service.delete("nope")).isFalse();
    }

    @Test
    @DisplayName("创建后版本号为 1")
    void createSetsVersionOne() {
        service.createPath("p", "learn X", "New");

        assertThat(store.load("p").getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("空路径不含模块，仍可创建")
    void createEmptyPath() {
        service.createPath("p", "", "");

        LearningProgress loaded = store.load("p");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getModules()).isEmpty();
    }

    @Test
    @DisplayName("写入带知识点的路径后可按 id 找回")
    void prebuiltPathIsReadable() {
        store.create(ServiceFixtures.onePoint("p", KnowledgeType.MEMORY));

        LearningProgress loaded = store.load("p");
        assertThat(loaded.findKnowledgePoint("k1").found()).isTrue();
    }
}
