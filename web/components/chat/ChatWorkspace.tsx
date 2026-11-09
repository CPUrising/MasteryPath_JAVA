"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslation } from "react-i18next";

import { MessageComposer, type ComposerAttachment } from "@/components/common/MessageComposer";
import { ChatMessageList, type DisplayMessage } from "./ChatMessageList";
import { MasteryHandoffCards } from "./MasteryHandoffCard";
import { WelcomeMark } from "./WelcomeMark";
import { api } from "@/lib/api";
import { chatApi } from "@/lib/chat-api";
import { knowledgeApi } from "@/lib/knowledge-api";
import { chatSessionRoute } from "@/lib/learning-routes";
import { masteryHandoffFrom, type MasteryHandoffPayload } from "@/lib/mastery-handoff";
import type {
  ChatSessionDetail,
  ChatSessionSummary,
  KnowledgeBase,
  ModelEntry,
  ModelSection,
} from "@/lib/types";

function greetingKey(date: Date): string {
  const hour = date.getHours();
  if (hour < 12) return "chat.greeting.morning";
  if (hour < 18) return "chat.greeting.afternoon";
  return "chat.greeting.evening";
}

function toDisplayMessages(detail: ChatSessionDetail): DisplayMessage[] {
  return detail.messages.map((message) => ({
    id: `m-${message.id}`,
    role: message.role,
    content: message.content,
    attachments: message.attachments ?? [],
    serverId: message.id,
  }));
}

export function ChatWorkspace({ sessionId }: { sessionId?: string }) {
  const { t } = useTranslation();
  const router = useRouter();
  const [session, setSession] = useState<ChatSessionSummary | null>(null);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [models, setModels] = useState<ModelEntry[]>([]);
  const [modelId, setModelId] = useState("");
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [kbIds, setKbIds] = useState<string[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const [handoffs, setHandoffs] = useState<MasteryHandoffPayload[]>([]);
  const [draft, setDraft] = useState<{ text: string; nonce: number } | null>(null);
  const [greeting] = useState(() => greetingKey(new Date()));

  const refreshSessions = useCallback(async () => {
    try {
      await chatApi.list();
    } catch {
      /* the sidebar refreshes on navigation */
    }
  }, []);

  const applyDetail = useCallback((detail: ChatSessionDetail) => {
    setSession(detail.session);
    setMessages(toDisplayMessages(detail));
  }, []);

  useEffect(() => {
    api
      .getSettings("llm")
      .then((value) => {
        const section = value as ModelSection;
        setModels(section.models ?? []);
        setModelId(section.active_id || section.models?.[0]?.id || "");
      })
      .catch(() => setModels([]));
    knowledgeApi.list().then(setKnowledgeBases).catch(() => setKnowledgeBases([]));
  }, []);

  useEffect(() => {
    setHandoffs([]);
    setDraft(null);
    if (!sessionId) {
      setSession(null);
      setMessages([]);
      return;
    }
    chatApi
      .get(sessionId)
      .then((detail) => {
        applyDetail(detail);
        setModelId(detail.session.model_id || modelId);
        setKbIds(detail.session.kb_ids || []);
      })
      .catch(() => setError(t("chat.loadFailed")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  const changeModel = (next: string) => {
    setModelId(next);
    if (session) void chatApi.update(session.session_id, { model_id: next }).catch(() => undefined);
  };

  const changeKb = (next: string[]) => {
    setKbIds(next);
    if (session) void chatApi.update(session.session_id, { kb_ids: next }).catch(() => undefined);
  };

  /** 新消息落库后重新拉取，拿到服务端 id，撤回才有依据。 */
  const syncMessages = async (sid: string) => {
    try {
      applyDetail(await chatApi.get(sid));
    } catch {
      /* keep the streamed copy if the reload fails */
    }
  };

  const send = async (text: string, attachments: ComposerAttachment[]) => {
    setError("");
    setStreaming(true);
    setHandoffs([]);
    const userMessage: DisplayMessage = {
      id: `u-${Date.now()}`,
      role: "user",
      content: text,
      attachments: attachments.map((item) => ({ name: item.name })),
    };
    const assistantMessage: DisplayMessage = {
      id: `a-${Date.now()}`,
      role: "assistant",
      content: "",
      attachments: [],
    };
    setMessages((current) => [...current, userMessage, assistantMessage]);
    let sid: string | null = null;
    try {
      let active = session;
      if (!active) {
        active = await chatApi.create({ model_id: modelId, kb_ids: kbIds });
        setSession(active);
        router.replace(chatSessionRoute(active.session_id));
      }
      sid = active.session_id;
      await chatApi.streamTurn(
        sid,
        text,
        attachments.map((item) => ({ name: item.name, text: item.text })),
        (event) => {
          const data = event.data ?? {};
          if (event.type === "text_delta" && typeof data.text === "string") {
            setMessages((current) =>
              current.map((message) =>
                message.id === assistantMessage.id
                  ? { ...message, content: message.content + (data.text as string) }
                  : message,
              ),
            );
          } else if (event.type === "tool_result") {
            const handoff = masteryHandoffFrom(event);
            if (handoff) {
              setHandoffs((current) =>
                current.some(
                  (item) =>
                    item.kind === handoff.kind &&
                    item.path_id === handoff.path_id &&
                    item.session_id === handoff.session_id &&
                    item.module_id === handoff.module_id,
                )
                  ? current
                  : [...current, handoff],
              );
            }
          } else if (event.type === "error") {
            setError(typeof data.message === "string" ? data.message : t("chat.sendFailed"));
            setMessages((current) => current.filter((message) => message.id !== assistantMessage.id));
          } else if (event.type === "turn_end" && typeof data.title === "string" && data.title) {
            setSession((current) => (current ? { ...current, title: data.title as string } : current));
          }
        },
      );
      void refreshSessions();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t("chat.sendFailed"));
      setMessages((current) => current.filter((message) => message.id !== assistantMessage.id));
    } finally {
      setStreaming(false);
      if (sid) void syncMessages(sid);
    }
  };

  /** 撤回：删除该条用户消息及其之后的所有消息，并把原文放回输入框。 */
  const withdraw = async (message: DisplayMessage) => {
    if (streaming) return;
    setError("");
    const index = messages.findIndex((item) => item.id === message.id);
    if (index < 0) return;
    try {
      if (session && message.serverId != null) {
        const response = await chatApi.withdraw(session.session_id, message.serverId);
        if (!response.ok && response.status !== 404) {
          throw new Error(`${response.status}`);
        }
      }
      setMessages((current) => current.slice(0, index));
      setDraft({ text: message.content, nonce: Date.now() });
      void refreshSessions();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t("chat.withdrawFailed"));
    }
  };

  const title = useMemo(() => session?.title || t("chat.newChat"), [session, t]);

  return (
    <div className="flex h-full flex-col bg-background text-foreground">
      <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-5">
        <h1 className="min-w-0 flex-1 truncate text-[14px] font-semibold">{title}</h1>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 px-6 text-center">
            <WelcomeMark />
            <p className="text-xl font-medium tracking-tight">{t(greeting)}</p>
          </div>
        ) : (
          <>
            <ChatMessageList messages={messages} streaming={streaming} onWithdraw={withdraw} />
            {handoffs.length > 0 && (
              <div className="mx-auto w-full max-w-3xl px-4 pb-4">
                <MasteryHandoffCards handoffs={handoffs} />
              </div>
            )}
          </>
        )}
      </div>

      {error && (
        <p role="alert" className="mx-auto w-full max-w-3xl px-4 pb-2 text-[12px] text-destructive">
          {error}
        </p>
      )}

      <MessageComposer
        models={models}
        modelId={modelId}
        onModelChange={changeModel}
        knowledgeBases={knowledgeBases}
        kbIds={kbIds}
        onKbChange={changeKb}
        streaming={streaming}
        onSend={(text, attachments) => void send(text, attachments)}
        capabilityLabel={t("chat.capability")}
        draft={draft ?? undefined}
      />
    </div>
  );
}
