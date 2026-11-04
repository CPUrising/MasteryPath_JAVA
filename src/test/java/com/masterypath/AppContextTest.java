package com.masterypath;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * G0 冒烟：Spring 上下文能装配起来。
 *
 * <p>只验证「工程骨架可用」，不覆盖任何业务需求（业务用例从 0002 开始）。
 */
@SpringBootTest
class AppContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(App.class)).isNotNull();
    }

    @Test
    void runsOnJava21() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
