package com.masterypath.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masterypath.domain.model.LearningProgress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TC-B9…B13、B17：真实多线程 + 真实 SQLite，<b>禁止 mock</b>（spec 03 §B）。
 *
 * <p>这些用例断言的是「最终状态」与「异常类型」，不断言具体时序——
 * 时序依赖会让用例在慢机器上偶发失败，从而变成假阴性。
 */
@Tag("storage")
class LearningStoreConcurrencyTest {

    @TempDir
    Path tempDir;

    private LearningStore store;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    /** TC-B9：可合并的并发写全部生效（CAS 重试把对方的修改保留下来）。 */
    @Test
    void mergeableConcurrentWritesAllApply() throws Exception {
        store.create(new LearningProgress("p"));
        int writers = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(writers);
        for (int i = 0; i < writers; i++) {
            final String key = "k" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    store.mutate("p", tx -> {
                        tx.progress().getExplainedObjectives().put(key, Map.of());
                        return null;
                    });
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        Set<String> keys = store.load("p").getExplainedObjectives().keySet();
        assertThat(keys).isEqualTo(IntStream.range(0, writers)
                .mapToObj(i -> "k" + i).collect(Collectors.toSet()));
    }

    /** TC-B10：前置条件失败不重试、不落库。 */
    @Test
    void preconditionNotRetriedAndNoWrite() {
        store.create(new LearningProgress("p"));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> store.mutate("p", tx -> {
            calls.incrementAndGet();
            tx.progress().getExplainedObjectives().put("k", Map.of());
            tx.require(false, "precondition not met");
            return null;
        })).isInstanceOf(PreconditionFailedException.class);

        assertThat(calls.get()).as("前置条件失败 MUST NOT 重试").isEqualTo(1);
        assertThat(store.load("p").getVersion()).isEqualTo(1);
        assertThat(store.eventsSince("p", 0)).isEmpty();
    }

    /** TC-B11：冲突达上限 → 抛 CasConflictException，且重试次数恰为配置值。 */
    @Test
    void casExhaustionThrowsAfterConfiguredRetries() {
        AtomicInteger attempts = new AtomicInteger();
        LearningStore strict = new LearningStore(tempDir.resolve("strict"), 3);
        try {
            int thrown = 0;
            try {
                strict.retryCas(() -> {
                    attempts.incrementAndGet();
                    throw new CasConflictException("forced");
                });
            } catch (CasConflictException e) {
                thrown = 1;
            }
            assertThat(thrown).isEqualTo(1);
            assertThat(attempts.get()).as("重试上限为 3 次").isEqualTo(3);
        } finally {
            strict.close();
        }
    }

    /** TC-B12：并发写不同路径，都成功且互不覆盖，不冒出数据库忙错误。 */
    @Test
    void concurrentDifferentPaths() throws Exception {
        store.create(new LearningProgress("a"));
        store.create(new LearningProgress("b"));
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> runGuarded(start, errors, () -> store.mutate("a", tx -> {
            tx.progress().getExplainedObjectives().put("ka", Map.of());
            return null;
        })));
        pool.submit(() -> runGuarded(start, errors, () -> store.mutate("b", tx -> {
            tx.progress().getExplainedObjectives().put("kb", Map.of());
            return null;
        })));
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(store.load("a").getExplainedObjectives().keySet()).containsExactly("ka");
        assertThat(store.load("b").getExplainedObjectives().keySet()).containsExactly("kb");
    }

    /** TC-B13：并发写同一路径，两者都最终生效（CAS 重试）或一方冲突，绝不丢更新。 */
    @Test
    void concurrentSamePathNeverLosesUpdate() throws Exception {
        store.create(new LearningProgress("p"));
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            final String key = "k" + i;
            pool.submit(() -> runGuarded(start, errors, () -> store.mutate("p", tx -> {
                tx.progress().getExplainedObjectives().put(key, Map.of());
                return null;
            })));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 允许 CasConflictException（重试耗尽），但不允许其他异常。
        assertThat(errors).allMatch(t -> t instanceof CasConflictException);
        LearningProgress loaded = store.load("p");
        assertThat(loaded.getExplainedObjectives()).isNotEmpty();
        // 无论哪几个成功，版本号必须与成功次数一致，不能出现「覆盖他人」。
        int succeeded = 4 - errors.size();
        assertThat(loaded.getVersion()).isEqualTo(1 + succeeded);
    }

    /**
     * TC-B14：写入进行中，另一个连接读取不被阻塞（WAL 下读到旧快照）。
     *
     * <p>必须用<b>独立连接</b>才能验证 WAL 的价值：同一 {@code LearningStore} 是单连接 + 进程内互斥，
     * 其读取本就会被自己的事务串行化。这里模拟「另一个进程/另一个 workspace 句柄」的读取。
     */
    @Test
    void readerOnSeparateConnectionNotBlockedDuringWrite() throws Exception {
        store.create(new LearningProgress("p"));
        store.mutate("p", tx -> {
            tx.progress().setName("committed");
            return null;
        });

        CountDownLatch insideCallback = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);

        Thread writer = new Thread(() -> store.mutate("p", tx -> {
            tx.progress().setName("uncommitted");
            insideCallback.countDown();
            try {
                releaseCallback.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        writer.start();
        assertThat(insideCallback.await(10, TimeUnit.SECONDS)).isTrue();

        // 独立连接读：MUST 立即返回且看到已提交的旧值，而不是被写事务阻塞。
        long startedAt = System.nanoTime();
        String seen;
        try (var other = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + store.dbPath().toAbsolutePath());
             var ps = other.prepareStatement("SELECT data FROM paths WHERE path_id=?")) {
            ps.setString(1, "p");
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                seen = rs.getString("data");
            }
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        releaseCallback.countDown();
        writer.join(10_000);

        assertThat(writer.isAlive()).isFalse();
        assertThat(elapsedMs).as("独立连接读取 MUST NOT 被写事务阻塞").isLessThan(2_000);
        assertThat(seen).as("应读到已提交的旧快照").contains("committed").doesNotContain("uncommitted");
    }

    /** TC-B17：跨 workspace 并发写，各自成功、互不可见。 */
    @Test
    void crossWorkspaceConcurrent() throws Exception {
        Path wsA = tempDir.resolve("ws_a");
        Path wsB = tempDir.resolve("ws_b");
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        pool.submit(() -> runGuarded(start, errors, () -> {
            try (LearningStore a = new LearningStore(wsA)) {
                LearningProgress progress = new LearningProgress("p");
                progress.setName("A");
                a.create(progress);
                a.mutate("p", tx -> {
                    tx.progress().getExplainedObjectives().put("ka", Map.of());
                    return null;
                });
            }
            return null;
        }));
        pool.submit(() -> runGuarded(start, errors, () -> {
            try (LearningStore b = new LearningStore(wsB)) {
                LearningProgress progress = new LearningProgress("p");
                progress.setName("B");
                b.create(progress);
                b.mutate("p", tx -> {
                    tx.progress().getExplainedObjectives().put("kb", Map.of());
                    return null;
                });
            }
            return null;
        }));
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        try (LearningStore a = new LearningStore(wsA);
             LearningStore b = new LearningStore(wsB)) {
            assertThat(a.load("p").getName()).isEqualTo("A");
            assertThat(a.load("p").getExplainedObjectives().keySet()).containsExactly("ka");
            assertThat(b.load("p").getName()).isEqualTo("B");
            assertThat(b.load("p").getExplainedObjectives().keySet()).containsExactly("kb");
        }
    }

    private void runGuarded(CountDownLatch start, List<Throwable> errors, java.util.function.Supplier<Object> body) {
        try {
            start.await();
            body.get();
        } catch (Throwable t) {
            errors.add(t);
        }
    }
}
