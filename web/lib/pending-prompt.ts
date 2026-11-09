const KEY = "masterypath.pending-prompt.mastery";

export function setPendingPrompt(text: string): void {
  if (typeof window === "undefined") return;
  const value = text.trim();
  if (value) sessionStorage.setItem(KEY, value);
}

export function takePendingPrompt(): string {
  if (typeof window === "undefined") return "";
  const value = sessionStorage.getItem(KEY) || "";
  sessionStorage.removeItem(KEY);
  return value;
}
