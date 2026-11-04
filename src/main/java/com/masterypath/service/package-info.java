/**
 * 服务层：业务编排（大纲、出题判分、复习、修复、导航、知识库）。
 *
 * <p>事务边界与参考实现一致：一次工具调用 = 一次 {@code mutate}。
 * 本层可以依赖 storage 与 domain，但 MUST NOT 直接依赖 Web 层类型。
 */
package com.masterypath.service;
