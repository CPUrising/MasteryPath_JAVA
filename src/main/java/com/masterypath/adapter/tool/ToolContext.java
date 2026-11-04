package com.masterypath.adapter.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次工具调用的上下文（参考实现 {@code ToolContext}）。
 *
 * <p><b>可变</b>：工具会改写 {@link #setSessionMode} / {@link #setPathId} /
 * {@link #setModeChanged} / {@link #setPathChanged}，并调用 {@link #endTurn()}。
 * 因此不能用 record。
 *
 * <p>{@link #endTurn()} 是「出题即结束回合」的信号（{@code INV-44}）：
 * 工具调用它，回合循环在产出 {@code tool_result} 之后以 {@code reason=card} 结束。
 */
public class ToolContext {

    private final Object service;
    private String pathId;
    private Object sessionMode;
    private String sessionId = "";
    private String turnId = "";
    private Runnable endTurn;
    private java.util.function.Consumer<String> bindMode;
    private java.util.function.Consumer<String> bindPath;
    private boolean modeChanged;
    private boolean pathChanged;
    private Object knowledgeService;
    private List<String> kbIds = new ArrayList<>();
    private Map<String, String> sourceIndex = new LinkedHashMap<>();

    public ToolContext(Object service, String pathId) {
        this.service = service;
        this.pathId = pathId == null ? "" : pathId;
    }

    public Object service() {
        return service;
    }

    public String pathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId == null ? "" : pathId;
    }

    public Object sessionMode() {
        return sessionMode;
    }

    public void setSessionMode(Object sessionMode) {
        this.sessionMode = sessionMode;
    }

    public String sessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String turnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId == null ? "" : turnId;
    }

    /** 通知回合循环「本回合到此为止」（出题后使用）。 */
    public void endTurn() {
        if (endTurn != null) {
            endTurn.run();
        }
    }

    public void setEndTurn(Runnable endTurn) {
        this.endTurn = endTurn;
    }

    /** 切换模式的回调；为 {@code null} 时工具直接改 {@link #sessionMode}。 */
    public java.util.function.Consumer<String> bindMode() {
        return bindMode;
    }

    public void setBindMode(java.util.function.Consumer<String> bindMode) {
        this.bindMode = bindMode;
    }

    /** 切换路径的回调；为 {@code null} 时工具直接改 {@link #pathId}。 */
    public java.util.function.Consumer<String> bindPath() {
        return bindPath;
    }

    public void setBindPath(java.util.function.Consumer<String> bindPath) {
        this.bindPath = bindPath;
    }

    public boolean modeChanged() {
        return modeChanged;
    }

    public void setModeChanged(boolean modeChanged) {
        this.modeChanged = modeChanged;
    }

    public boolean pathChanged() {
        return pathChanged;
    }

    public void setPathChanged(boolean pathChanged) {
        this.pathChanged = pathChanged;
    }

    public Object knowledgeService() {
        return knowledgeService;
    }

    public void setKnowledgeService(Object knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public List<String> kbIds() {
        return kbIds;
    }

    public void setKbIds(List<String> kbIds) {
        this.kbIds = kbIds == null ? new ArrayList<>() : new ArrayList<>(kbIds);
    }

    public Map<String, String> sourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(Map<String, String> sourceIndex) {
        this.sourceIndex = sourceIndex == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sourceIndex);
    }
}
