# 0030-test-hygiene

测试基建治理：用 JUnit 标签替代手写 `-Dtest` 白名单，并让 `@SpringBootTest` 每次运行使用独立的临时数据目录。
