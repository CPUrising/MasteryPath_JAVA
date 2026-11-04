/**
 * 模型访问适配层（{@code ADR-014}）。
 *
 * <p>供应商差异（工具调用格式、流式协议、错误码）只在本包吸收，MUST NOT 泄漏进 domain。
 * {@link com.masterypath.adapter.llm.FakeLlmClient} 保证全部测试无需网络与 API Key。
 */
package com.masterypath.adapter.llm;
