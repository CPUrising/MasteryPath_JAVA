/**
 * 存储层：SQLite 连接、DDL、路径聚合的读取与 CAS 写入、事件表、会话与设置。
 *
 * <p>表结构与参考实现 DDL 逐字一致，便于直接用旧库做数据兼容回归。
 * 所有写入 MUST 经 {@code mutate(pathId, fn)}，禁止绕过 CAS 直接 UPDATE。
 */
package com.masterypath.storage;
