import type { ToolActivityEntry } from "./types";

/** 折叠行参数摘要里单个值的截断长度。 */
const ARG_VALUE_LIMIT = 80;

/** 追加一条「调用中」的工具动作。 */
export function addToolCall(
  activities: ToolActivityEntry[],
  name: string,
  args: Record<string, unknown>,
): ToolActivityEntry[] {
  return [
    ...activities,
    {
      id: `${name}-${activities.length}`,
      name,
      arguments: args,
      status: "running",
    },
  ];
}

/**
 * 把一条 tool_result 落到最近的同名「调用中」动作上。
 *
 * 找不到时原样返回：掌握之路的作答/跳过会先产出孤立的 tool_result。
 */
export function resolveToolResult(
  activities: ToolActivityEntry[],
  name: string,
  ok: boolean,
  content: string,
): ToolActivityEntry[] {
  for (let index = activities.length - 1; index >= 0; index -= 1) {
    const item = activities[index];
    if (item.name === name && item.status === "running") {
      const next = [...activities];
      next[index] = { ...item, status: ok ? "ok" : "failed", content };
      return next;
    }
  }
  return activities;
}

/** 折叠行的一行参数摘要：最多两个键，单值过长时截断。 */
export function summarizeArguments(args: Record<string, unknown>): string {
  const parts: string[] = [];
  for (const [key, value] of Object.entries(args ?? {})) {
    if (value === null || value === undefined || value === "") continue;
    const text = typeof value === "string" ? value : JSON.stringify(value) ?? "";
    parts.push(`${key}=${clip(text, ARG_VALUE_LIMIT)}`);
    if (parts.length >= 2) break;
  }
  return parts.join(" ");
}

function clip(value: string, limit: number): string {
  return value.length <= limit ? value : `${value.slice(0, limit)}…`;
}
