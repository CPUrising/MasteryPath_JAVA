package com.masterypath;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * G0 冒烟：包分层在第一天就固定，防止后续 change 误删或搬动顶层包。
 *
 * <p>用 {@code package-info.class} 作为包存在的证据：该文件随 {@code package-info.java} 一起编译，
 * 不依赖 {@code Package.getPackage}（Java 9 起已废弃且对无类的包不可靠）。
 * 分层约束本身（domain 不得依赖 Spring/JDBC）在 0002 引入 ArchUnit 后强制。
 */
class PackageLayoutTest {

    private static final String[] LAYERS = {"domain", "storage", "service", "adapter", "cli"};

    @Test
    void allLayersExist() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String layer : LAYERS) {
            String resource = "com/masterypath/" + layer + "/package-info.class";
            assertThat(loader.getResource(resource))
                    .as("缺少顶层包 %s（见 docs/migration-plan.md §3）", "com.masterypath." + layer)
                    .isNotNull();
        }
    }
}
