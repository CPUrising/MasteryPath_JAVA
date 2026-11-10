package com.masterypath.adapter.prompt;

import com.masterypath.domain.mode.SessionMode;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * mastery 提示词包（参考实现 {@code capabilities/mastery/prompts/zh/mastery_loop.yaml}）。
 *
 * <p>只负责取值与填空，不做装配——块顺序在 {@link MasteryPromptAssembler}。
 * 资源缺失或解析失败一律退化为空串：提示词包坏掉不能拖垮回合。
 *
 * <p>语言指令与 {@code runtime_context} / {@code tool_call_policy} 的兜底文案来自
 * chat 包（参考实现 {@code prompt_base_module = "chat"}）。
 */
public final class MasteryPrompts {

    private static final String MASTERY_PACK = "/prompts/zh/mastery_loop.yaml";
    private static final String CHAT_PACK = "/prompts/zh/agentic_chat.yaml";
    private static final String HINT_PREFIX = "/prompts/zh/hints/";

    private static final MasteryPrompts SHARED = new MasteryPrompts();

    /** 语言标签（参考实现 {@code _LANGUAGE_LABELS}）。 */
    private static final Map<String, String> LANGUAGE_LABELS = Map.ofEntries(
            Map.entry("zh", "中文（简体）"),
            Map.entry("zh-cn", "中文（简体）"),
            Map.entry("zh-tw", "繁體中文"),
            Map.entry("en", "English"),
            Map.entry("ja", "日本語"),
            Map.entry("ko", "한국어"),
            Map.entry("es", "Español"),
            Map.entry("fr", "Français"),
            Map.entry("de", "Deutsch"),
            Map.entry("ru", "Русский"),
            Map.entry("uk", "Українська"),
            Map.entry("pt", "Português"),
            Map.entry("it", "Italiano"),
            Map.entry("ar", "العربية"),
            Map.entry("pl", "Polski"),
            Map.entry("ms", "Bahasa Melayu"));

    private final Map<String, Object> mastery;
    private final Map<String, Object> chat;
    private final Map<String, Map<String, Object>> hints = new LinkedHashMap<>();

    private MasteryPrompts() {
        this(load(MASTERY_PACK), load(CHAT_PACK));
    }

    MasteryPrompts(Map<String, Object> mastery, Map<String, Object> chat) {
        this.mastery = mastery == null ? Map.of() : mastery;
        this.chat = chat == null ? Map.of() : chat;
    }

    /** 进程内共享实例（资源只读，解析结果可复用）。 */
    public static MasteryPrompts shared() {
        return SHARED;
    }

    // -- mastery 包 -----------------------------------------------------------

    public String general() {
        return text(mastery, "general");
    }

    /** 当前模式的职责段落；未知模式按 {@code study} 归一。 */
    public String session(Object modeValue) {
        String mode = SessionMode.normalize(modeValue).value();
        return text(mastery, "session." + mode);
    }

    public String runtimePolicy() {
        return text(mastery, "runtime_policy");
    }

    public String loopSystem() {
        return text(mastery, "loop.system");
    }

    public String loopUser() {
        String template = text(mastery, "loop.user");
        return template.isEmpty() ? "{user_message}" : template;
    }

    public String playbook() {
        return text(mastery, "playbook");
    }

    public String finishExhausted() {
        return text(mastery, "loop.finish_exhausted");
    }

    public String settleExhausted() {
        return text(mastery, "loop.settle_exhausted");
    }

    // -- chat 包兜底 ----------------------------------------------------------

    /** 运行时日期块；中文按 `yyyy年M月d日` 渲染。 */
    public String runtimeContext(LocalDate date) {
        String template = text(chat, "runtime_context");
        if (template.isEmpty() || date == null) {
            return template;
        }
        String rendered = date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
        return template.replace("{datetime}", rendered);
    }

    /** 单轮工具调用上限。 */
    public String toolCallPolicy(int limit) {
        return text(chat, "tool_call_policy").replace("{limit}", String.valueOf(limit));
    }

    // -- 工具清单 -------------------------------------------------------------

    /**
     * 工具清单（复刻 {@code format_list_with_usage}）。
     *
     * <p>没有 hint 资产的工具不出现；清单为空时返回 {@code - 无}
     * （复刻 {@code _fallback_empty_tool_list}）。
     */
    public String toolManifest(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return "- 无";
        }
        List<String> entries = new java.util.ArrayList<>();
        for (String name : new LinkedHashSet<>(names)) {
            Map<String, Object> hint = hint(name);
            String shortDescription = text(hint, "short_description");
            if (shortDescription.isEmpty()) {
                continue;
            }
            List<String> lines = new java.util.ArrayList<>();
            lines.add("- `" + name + "` — " + shortDescription);
            String whenToUse = text(hint, "when_to_use");
            if (!whenToUse.isEmpty()) {
                lines.add("    适用场景: " + whenToUse);
            }
            String inputFormat = text(hint, "input_format");
            if (!inputFormat.isEmpty()) {
                lines.add("    参数格式: " + inputFormat);
            }
            entries.add(String.join("\n", lines));
        }
        return entries.isEmpty() ? "- 无" : String.join("\n", entries);
    }

    // -- 语言 -----------------------------------------------------------------

    /**
     * 语言指令（复刻 {@code language_directive}）。
     *
     * @param language         响应用语言（{@code general.response_language}）
     * @param allowUserOverride 学习者可以要求改用其它语言
     */
    public String languageDirective(String language, boolean allowUserOverride) {
        String code = language == null ? "en" : language.strip().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) {
            code = "en";
        }
        String label = label(code);
        String override = "";
        String body;
        if (code.startsWith("zh")) {
            body = "\n\n[语言要求 / Language] "
                    + "请严格使用" + label + "撰写所有面向读者的文本（标题、正文、解释、提示、过渡句、"
                    + "题干、选项等），即使参考资料、JSON 字段名或英文术语出现在 prompt 中也"
                    + "不得切换语言；保留必要的专有名词原文（如人名、产品名、公式中的变量符号"
                    + "等）即可，其余一律使用" + label + "。";
            override = "以上是默认输出语言；若用户在对话中明确要求改用其他语言作答，"
                    + "则以用户当次的要求为准，并在本次对话中延续该语言。";
        } else if ("en".equals(code)) {
            body = "\n\n[Language] Write ALL reader-facing text (titles, prose, "
                    + "explanations, hints, transitions, quiz stems, options, etc.) in "
                    + "English. Do NOT switch languages even if the source material, "
                    + "JSON keys, or examples in this prompt are in another language. "
                    + "Keep proper nouns (people, products, formula symbols) in their "
                    + "original form.";
            override = "This is the default output language, not a restriction on the reader: if "
                    + "the user explicitly asks you to answer in another language, honour that "
                    + "request and keep to it for the rest of the conversation.";
        } else {
            body = "\n\n[Language] Write ALL reader-facing text strictly in " + label + ". "
                    + "Do NOT switch languages even if the source material, JSON keys, or "
                    + "examples in this prompt are in a different language. Keep proper "
                    + "nouns (people, products, formula symbols) in their original form.";
            override = "This is the default output language, not a restriction on the reader: if "
                    + "the user explicitly asks you to answer in another language, honour that "
                    + "request and keep to it for the rest of the conversation.";
        }
        String directive = allowUserOverride ? body + " " + override : body;
        return directive.strip();
    }

    private static String label(String code) {
        String direct = LANGUAGE_LABELS.get(code);
        if (direct != null) {
            return direct;
        }
        String base = code.split("-", 2)[0];
        return LANGUAGE_LABELS.getOrDefault(base, code);
    }

    // -- 辅助 -----------------------------------------------------------------

    private Map<String, Object> hint(String name) {
        if (name == null || name.isBlank()) {
            return Map.of();
        }
        return hints.computeIfAbsent(name, key -> load(HINT_PREFIX + key + ".yaml"));
    }

    /** 取 `a.b.c` 路径上的字符串；缺失或类型不符返回空串。 */
    private static String text(Map<String, Object> root, String path) {
        Object value = root;
        for (String part : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) {
                return "";
            }
            value = map.get(part);
        }
        return value instanceof String string ? string : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) {
        try (InputStream stream = MasteryPrompts.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return Map.of();
            }
            Object parsed = new Yaml().load(stream);
            return parsed instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }
}
