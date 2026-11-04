/**
 * 适配层：LLM 客户端、Agent Loop、回合编排、REST/WS 端点、工具注册、大纲评测。
 *
 * <p>供应商差异（工具调用格式、流式协议、错误码）只在本层吸收，MUST NOT 泄漏进 domain。
 * 必须提供可离线运行的假客户端，使全部测试无需网络与 API Key。
 */
package com.masterypath.adapter;
