package com.masterypath;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MasteryPath 后端入口。
 *
 * <p>本工程是参考实现（Python）的行为等价重写；对外契约（REST 路径、WS 事件、JSON 字段名）
 * 与领域规则（掌握门槛、判分、门控、SM-2）以 {@code openspec/specs/} 为准。
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
