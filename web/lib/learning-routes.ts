import type { Mode } from "./types";

export const MASTERY_HOME = "/learning/mastery";
export const CHAT_HOME = "/chat";
export const SPACE_HOME = "/space";
export const KNOWLEDGE_HOME = "/knowledge-bases";

export function chatSessionRoute(sessionId: string): string {
  return `${CHAT_HOME}/${encodeURIComponent(sessionId)}`;
}

export function knowledgeBaseRoute(kbId: string): string {
  return `${KNOWLEDGE_HOME}/${encodeURIComponent(kbId)}`;
}

export function masteryTopicRoute(pathId: string): string {
  return `${MASTERY_HOME}/${encodeURIComponent(pathId)}`;
}

export function masterySessionsRoute(pathId: string): string {
  return `${masteryTopicRoute(pathId)}/sessions`;
}

export function masterySessionRoute(
  pathId: string,
  mode: Mode,
  sessionId = "",
): string {
  const query = new URLSearchParams();
  if (mode !== "study") query.set("mode", mode);
  if (sessionId) query.set("session", sessionId);
  const params = query.toString();
  return `${masterySessionsRoute(pathId)}${params ? `?${params}` : ""}`;
}

export function settingsRoute(section: string): string {
  return `/settings/${section}`;
}
