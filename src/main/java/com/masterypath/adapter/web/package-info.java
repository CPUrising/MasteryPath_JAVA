/**
 * REST 传输层（参考实现 {@code api/app.py}）。
 *
 * <p>本层只做三件事：解析请求、调用 {@code service} / 工具层、把异常映射成 HTTP 状态码。
 * 业务判定一律在 {@code domain} 与 {@code service} 层，这里不重复实现。
 *
 * <p>错误码语义固定（{@code 0018-rest-api/design.md}）：
 * <ul>
 *   <li>{@code 404}：路径 / 会话 / 题库条目 / 设置分组不存在。</li>
 *   <li>{@code 409}：模式冲突、重建未确认、准入失败、路径已存在。</li>
 *   <li>{@code 412}：仅 {@code objectives/{kp}/override} 使用。</li>
 *   <li>{@code 422}：payload 非法、工具拒绝、缺口 / 候选动作非法。</li>
 * </ul>
 */
package com.masterypath.adapter.web;
