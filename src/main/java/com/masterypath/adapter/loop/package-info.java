/**
 * 回合循环（{@code ADR-001}）。
 *
 * <p>循环只依赖 {@link com.masterypath.adapter.llm.LlmClient} 与
 * {@link com.masterypath.adapter.tool.Tool}，不依赖任何具体工具或服务层，
 * 因此可用假工具完全离线测试。
 */
package com.masterypath.adapter.loop;
