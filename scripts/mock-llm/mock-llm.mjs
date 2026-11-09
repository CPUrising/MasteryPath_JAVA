#!/usr/bin/env node
/**
 * 本地 OpenAI 兼容假模型服务（只用于开发与手工演示）。
 *
 * 它模拟的是「供应商 HTTP 端点」，不进入 Spring 上下文，也不被任何自动化测试引用。
 * 用法：
 *   node scripts/mock-llm/mock-llm.mjs [--port 9099] [--delay-ms 25] [--model mock-tutor]
 *
 * 然后在 data/settings.json（或设置页）把 provider 配成：
 *   base_url = http://127.0.0.1:9099/v1
 *   api_key  = mock（任意非空字符串）
 *   model    = mock-tutor
 *
 * 剧本（按系统提示里的 `Current mode:` 与最后一条用户消息决定）：
 *   - 最后一条消息是 tool         → 输出一段带工具结果的收尾文本
 *   - outline + 「大纲/规划/开始学」 → 调用 mastery_build
 *   - study   + 「出题/考我/练习」   → 调用 mastery_quiz
 *   - 其余                          → 输出固定 Markdown 文本
 *
 * 工具只有在请求的 tools 里真实存在时才会被调用，因此模式门控的行为仍然是真的。
 */

import http from "node:http";

const argv = process.argv.slice(2);

function flag(name, fallback) {
  const index = argv.indexOf(`--${name}`);
  return index >= 0 && argv[index + 1] ? argv[index + 1] : fallback;
}

if (argv.includes("--help") || argv.includes("-h")) {
  console.log(`用法: node scripts/mock-llm/mock-llm.mjs [--port 9099] [--delay-ms 25] [--model mock-tutor]`);
  process.exit(0);
}

const PORT = Number(flag("port", "9099"));
const DELAY_MS = Math.max(0, Number(flag("delay-ms", "25")));
const MODEL = flag("model", "mock-tutor");
/** 每个 SSE 文本片的最大字符数：越小越能看清逐字渲染。 */
const CHUNK_SIZE = 6;
/** 收尾文本里回显工具结果的最大长度，避免把整份状态快照灌进对话。 */
const TOOL_ECHO_LIMIT = 400;

// -- 剧本内容 ---------------------------------------------------------------

const BUILD_ARGS = {
  modules: [
    {
      name: "并发基础",
      knowledge_points: [
        { name: "线程与进程的区别", type: "memory" },
        { name: "synchronized 的可见性与互斥", type: "concept" },
      ],
    },
    {
      name: "并发实践",
      knowledge_points: [
        { name: "用 ThreadPoolExecutor 配置线程池", type: "procedure" },
        { name: "设计一个无锁计数器", type: "design" },
      ],
    },
  ],
};

const QUIZ_ARGS = {
  knowledge_point_id: "k1",
  question: "下面哪一项最能描述 `synchronized` 的作用？",
  expected_answer: "B",
  question_type: "choice",
  options: [
    { label: "A", body: "让线程休眠一段时间" },
    { label: "B", body: "保证同一时刻只有一个线程进入临界区，并建立**可见性**" },
    { label: "C", body: "把方法编译成机器码" },
    { label: "D", body: "创建新的线程" },
  ],
  explanation: "`synchronized` 同时提供互斥与可见性；休眠是 `Thread.sleep` 的职责。",
  difficulty: "easy",
};

// -- 练习题卷剧本（deep_question） ------------------------------------------

function practiceQuizText(userText) {
  const countMatch = /Number of questions:\s*(\d+)/i.exec(userText);
  const count = Math.min(10, Math.max(1, Number(countMatch ? countMatch[1] : 3)));
  const topicMatch = /Topic:\s*(.+)/i.exec(userText);
  const topic = (topicMatch ? topicMatch[1] : "当前主题").trim();
  const templates = [
    {
      question_type: "choice",
      question: `关于「${topic}」，下面哪一项描述最准确？`,
      options: [
        { label: "A", body: "它与该主题完全无关" },
        { label: "B", body: `它解释了「${topic}」的核心机制` },
        { label: "C", body: "它只适用于旧版本" },
        { label: "D", body: "它无法被验证" },
      ],
      expected_answer: "B",
      explanation: "Mock LLM 的固定选择题：正确项是 B。",
      difficulty: "medium",
      knowledge_point_id: "",
    },
    {
      question_type: "short",
      question: `用一句话概括「${topic}」的核心思想。`,
      expected_answer: "核心思想",
      accepted_answers: ["核心思想", "核心是", "本质"],
      explanation: "Mock LLM 的固定短答题：包含「核心思想」即可判定正确。",
      difficulty: "medium",
      knowledge_point_id: "",
    },
    {
      question_type: "open",
      question: `简述「${topic}」的一个实际应用。`,
      expected_answer: "给出一个具体应用场景，并说明它解决的问题。",
      required_keywords: ["应用"],
      optional_keywords: ["场景"],
      explanation: "Mock LLM 的固定开放题：答案中包含「应用」即可判定通过。",
      difficulty: "medium",
      knowledge_point_id: "",
    },
  ];
  const questions = [];
  for (let index = 0; index < count; index += 1) {
    const template = templates[index % templates.length];
    questions.push({ ...template, question: `（第 ${index + 1} 题）${template.question}` });
  }
  return JSON.stringify({ questions });
}

// -- 请求解析 ---------------------------------------------------------------

function readJson(request) {
  return new Promise((resolve, reject) => {
    let raw = "";
    request.setEncoding("utf8");
    request.on("data", (piece) => {
      raw += piece;
    });
    request.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
    request.on("error", reject);
  });
}

function textOf(message) {
  const content = message && message.content;
  if (typeof content === "string") {
    return content;
  }
  if (Array.isArray(content)) {
    return content.map((part) => (part && typeof part.text === "string" ? part.text : "")).join("");
  }
  return "";
}

function systemPromptOf(messages) {
  const system = messages.find((message) => message && message.role === "system");
  return system ? textOf(system) : "";
}

function currentModeOf(messages) {
  const match = /Current mode:\s*([a-z_]+)/i.exec(systemPromptOf(messages));
  return match ? match[1].toLowerCase() : "study";
}

function lastUserTextOf(messages) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index] && messages[index].role === "user") {
      return textOf(messages[index]);
    }
  }
  return "";
}

function lastToolNameOf(messages) {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message && message.role === "assistant" && Array.isArray(message.tool_calls)) {
      const call = message.tool_calls[message.tool_calls.length - 1];
      const name = call && call.function && call.function.name;
      return typeof name === "string" ? name : "";
    }
  }
  return "";
}

function truncate(text, limit) {
  const value = String(text ?? "");
  return value.length <= limit ? value : `${value.slice(0, limit)}…`;
}

// -- 剧本选择 ---------------------------------------------------------------

function decide(body) {
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const mode = currentModeOf(messages);
  const userText = lastUserTextOf(messages);
  const lastMessage = messages[messages.length - 1] || {};
  const available = new Set(
    (Array.isArray(body.tools) ? body.tools : [])
      .map((tool) => tool && tool.function && tool.function.name)
      .filter((name) => typeof name === "string" && name.length > 0),
  );
  const toolName = lastToolNameOf(messages);

  if (/You generate practice quizzes/i.test(systemPromptOf(messages))) {
    return {
      kind: "text",
      mode,
      userText,
      toolName: "",
      text: practiceQuizText(userText),
    };
  }

  if (lastMessage.role === "tool") {
    return {
      kind: "text",
      mode,
      userText,
      toolName,
      text: [
        `**${toolName || "工具"} 已执行。**`,
        "",
        "```json",
        truncate(lastMessage.content, TOOL_ECHO_LIMIT),
        "```",
        "",
        "_以上是本地 Mock LLM 的固定收尾，用来验证「工具结果 → 再说话」这条链路。_",
      ].join("\n"),
    };
  }

  if (mode === "outline" && available.has("mastery_build")
    && /(大纲|规划|开始学|学习计划|outline|plan)/i.test(userText)) {
    return { kind: "tool", name: "mastery_build", arguments: BUILD_ARGS, mode, userText, toolName: "" };
  }

  if (mode === "study" && available.has("mastery_quiz")
    && /(出题|考我|练习|测我|quiz|test me)/i.test(userText)) {
    return { kind: "tool", name: "mastery_quiz", arguments: QUIZ_ARGS, mode, userText, toolName: "" };
  }

  if (available.has("mastery_topics")
    && /(主题|课程|topics|list my)/i.test(userText)) {
    return { kind: "tool", name: "mastery_topics", arguments: {}, mode, userText, toolName: "" };
  }

  return { kind: "text", mode, userText, toolName: "", text: defaultText(mode) };
}

function defaultText(mode) {
  return [
    "**本地 Mock LLM 已响应。**",
    "",
    `当前模式：\`${mode}\``,
    "",
    "- 这是一个固定的演示回复，用来验证**流式渲染**与 Markdown。",
    "- 想让我出题：在 `study` 模式输入「出题」。",
    "- 想建大纲：切到 `outline` 模式后输入「帮我规划大纲」。",
    "",
    "_本服务不联网、不花钱，仅用于开发联调。_",
  ].join("\n");
}

// -- 响应构造 ---------------------------------------------------------------

function chunkFrame(delta, finishReason = null) {
  return `data: ${JSON.stringify({
    id: "chatcmpl-mock",
    object: "chat.completion.chunk",
    created: Math.floor(Date.now() / 1000),
    model: MODEL,
    choices: [{ index: 0, delta, finish_reason: finishReason }],
  })}\n\n`;
}

function textFrames(text) {
  const frames = [];
  for (let start = 0; start < text.length; start += CHUNK_SIZE) {
    frames.push(chunkFrame({ content: text.slice(start, start + CHUNK_SIZE) }));
  }
  return frames;
}

function toolFrame(decision) {
  return chunkFrame({
    tool_calls: [{
      index: 0,
      id: `call_mock_${Date.now()}`,
      type: "function",
      function: {
        name: decision.name,
        arguments: JSON.stringify(decision.arguments),
      },
    }],
  });
}

function streamResponse(response, decision) {
  response.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });

  const frames = decision.kind === "tool"
    ? textFrames("好的，马上处理。\n\n").concat([toolFrame(decision)])
    : textFrames(decision.text);

  let index = 0;
  const writeNext = () => {
    if (index >= frames.length) {
      response.write("data: [DONE]\n\n");
      response.end();
      return;
    }
    response.write(frames[index]);
    index += 1;
    if (DELAY_MS > 0) {
      setTimeout(writeNext, DELAY_MS);
    } else {
      writeNext();
    }
  };
  writeNext();
}

function completeResponse(response, decision) {
  const message = decision.kind === "tool"
    ? {
      role: "assistant",
      content: "好的，马上处理。\n\n",
      tool_calls: [{
        id: `call_mock_${Date.now()}`,
        type: "function",
        function: { name: decision.name, arguments: JSON.stringify(decision.arguments) },
      }],
    }
    : { role: "assistant", content: decision.text };

  response.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify({
    id: "chatcmpl-mock",
    object: "chat.completion",
    created: Math.floor(Date.now() / 1000),
    model: MODEL,
    choices: [{
      index: 0,
      message,
      finish_reason: decision.kind === "tool" ? "tool_calls" : "stop",
    }],
  }));
}

// -- HTTP 路由 --------------------------------------------------------------

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://127.0.0.1:${PORT}`);

  if (request.method === "GET" && url.pathname.endsWith("/models")) {
    response.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
    response.end(JSON.stringify({
      object: "list",
      data: [{ id: MODEL, object: "model", owned_by: "mock-llm" }],
    }));
    return;
  }

  if (request.method === "POST" && url.pathname.endsWith("/chat/completions")) {
    let body;
    try {
      body = await readJson(request);
    } catch (error) {
      response.writeHead(400, { "Content-Type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({ error: { message: `bad json: ${error.message}` } }));
      return;
    }

    const decision = decide(body);
    const action = decision.kind === "tool" ? `tool:${decision.name}` : "text";
    console.log(
      `[mock-llm] ${new Date().toISOString()} mode=${decision.mode}`
      + ` last=${decision.kind === "tool" ? "user" : "assistant/tool"} -> ${action}`
      + ` user="${truncate(decision.userText, 40)}"`,
    );

    if (body.stream === false) {
      completeResponse(response, decision);
    } else {
      streamResponse(response, decision);
    }
    return;
  }

  response.writeHead(404, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify({ error: { message: "mock-llm 只实现了 GET /v1/models 与 POST /v1/chat/completions" } }));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[mock-llm] listening on http://127.0.0.1:${PORT}/v1`);
  console.log(`[mock-llm] model=${MODEL} delay=${DELAY_MS}ms`);
  console.log(`[mock-llm] 把 provider 的 base_url 指到这里，api_key 随便填一个非空字符串。`);
});

server.on("error", (error) => {
  console.error(`[mock-llm] 启动失败: ${error.message}`);
  process.exit(1);
});
