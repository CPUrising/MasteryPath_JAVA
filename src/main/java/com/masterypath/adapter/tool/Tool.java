package com.masterypath.adapter.tool;

import java.util.Map;

/**
 * 一个可挂载到回合循环的工具（参考实现 {@code tools.py} 里各 {@code Mastery*Tool} 的公共形状）。
 *
 * <p>schema 由工具自带（{@link #parameters()}），不从方法签名推导——参考实现也是手写
 * {@code TOOL_PARAMETERS}，因为执行体用 {@code params.get(...)} 取值，签名不可靠。
 *
 * <p>实现 MUST NOT 抛异常来表达「拒绝」：拒绝应返回 {@link ToolResult#failure(String)}。
 * 抛出的异常会被回合循环兜住并转成 {@code ok=false}，但那样会丢失精确文案。
 */
public interface Tool {

    /** 工具名（OpenAI function name）。 */
    String name();

    /** 给模型看的说明。 */
    String description();

    /** JSON Schema 的 {@code parameters} 部分。 */
    Map<String, Object> parameters();

    /**
     * 执行工具。
     *
     * @param params 模型给出的参数（可能为空 map，可能含多余键）
     * @param ctx    回合上下文
     * @return 结果；拒绝时用 {@code ok=false}
     */
    ToolResult execute(Map<String, Object> params, ToolContext ctx);
}
