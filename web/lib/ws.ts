import { WS_BASE } from "./api";
import type { LLMSelection, Mode, TurnEvent } from "./types";

export type TurnCapability = "mastery_path" | "deep_question";

export interface TurnRequest {
  session_id: string;
  path_id: string;
  mode: Mode;
  message: string;
  turn_id?: string;
  llm_selection?: LLMSelection;
  capability?: TurnCapability;
  num_questions?: number;
  difficulty?: string;
  mastery_answer?: { question_id: string; text: string };
  mastery_skip?: { question_id: string };
}

/** Run one tutor turn over the WebSocket and stream events back. */
export function runTurn(
  request: TurnRequest,
  onEvent: (event: TurnEvent) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${WS_BASE}/ws`);
    ws.onopen = () => ws.send(JSON.stringify(request));
    ws.onmessage = (message) => {
      try {
        onEvent(JSON.parse(message.data) as TurnEvent);
      } catch {
        /* ignore malformed frames */
      }
    };
    ws.onerror = () => reject(new Error("websocket error"));
    ws.onclose = () => resolve();
  });
}
