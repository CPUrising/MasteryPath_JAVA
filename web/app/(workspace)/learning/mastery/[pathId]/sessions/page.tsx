"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { useTranslation } from "react-i18next";

import { LevelUpCelebration } from "@/components/learning/LevelUpCelebration";
import { MessageBubble } from "@/components/common/MessageBubble";
import { MessageComposer } from "@/components/common/MessageComposer";
import { MasteryHandoffCards } from "@/components/chat/MasteryHandoffCard";
import { ModeSwitch } from "@/components/learning/ModeSwitch";
import { ProgressRing } from "@/components/learning/ProgressRing";
import { QuestionCard } from "@/components/learning/QuestionCard";
import { QuizViewer } from "@/components/learning/QuizViewer";
import { StudyOutline } from "@/components/learning/StudyOutline";
import { api, type GradeOutcome } from "@/lib/api";
import { knowledgeApi } from "@/lib/knowledge-api";
import { masteryHandoffFrom, type MasteryHandoffPayload } from "@/lib/mastery-handoff";
import { masterySessionRoute, masteryTopicRoute } from "@/lib/learning-routes";
import { MODE_NOTES, normalizeMode } from "@/lib/mastery-mode";
import { takePendingPrompt } from "@/lib/pending-prompt";
import type {
  KnowledgeBase,
  LLMSelection,
  Mode,
  ModelEntry,
  ModelSection,
  PathState,
  PendingQuestion,
  QuizPlan,
  QuizQuestion,
  QuizSet,
  SessionMessage,
} from "@/lib/types";
import { runTurn, type TurnCapability } from "@/lib/ws";

interface Item {
  role: "user" | "assistant";
  text: string;
  card?: PendingQuestion;
  grade?: GradeOutcome;
  quiz?: QuizSet;
  /** 生成进度条所属题卷；`quiz_set` 到达后移除。 */
  planSetId?: string;
  planCount?: number;
}

function masteredIds(state: PathState): Set<string> {
  return new Set(
    Object.entries(state.map)
      .filter(([, entry]) => entry.status === "mastered")
      .map(([kpId]) => kpId),
  );
}

function StudyRoute() {
  const params = useParams<{ pathId: string }>();
  const pathId = String(params.pathId || "");
  const search = useSearchParams();
  const router = useRouter();
  const { t } = useTranslation();
  const existingSession = search.get("session") || "";
  const sessionId = useMemo(
    () => existingSession || Math.random().toString(36).slice(2),
    [existingSession],
  );

  const [state, setState] = useState<PathState | null>(null);
  const [mode, setMode] = useState<Mode>(normalizeMode(search.get("mode")));
  const [capability, setCapability] = useState<TurnCapability>("mastery_path");
  const [items, setItems] = useState<Item[]>([]);
  const questionDrafts = useRef(new Map<string, QuizQuestion[]>());
  const [handoffs, setHandoffs] = useState<MasteryHandoffPayload[]>([]);
  const pendingSent = useRef(false);
  const [askHint, setAskHint] = useState("");
  const [busy, setBusy] = useState(false);
  const [outlineOpen, setOutlineOpen] = useState(true);
  const [celebration, setCelebration] = useState<string | null>(null);
  const [models, setModels] = useState<ModelEntry[]>([]);
  const [selection, setSelection] = useState<LLMSelection | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [kbIds, setKbIds] = useState<string[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [items, busy]);

  useEffect(() => {
    knowledgeApi.list().then(setKnowledgeBases).catch(() => setKnowledgeBases([]));
  }, []);

  useEffect(() => {
    api
      .getSettings("llm")
      .then((value) => {
        const section = value as ModelSection;
        setModels(section.models ?? []);
        setSelection((current) => {
          if (current) return current;
          const active = section.models?.find((model) => model.id === section.active_id) ??
            section.models?.[0];
          return active ? { provider_id: active.provider_id, model_id: active.id } : null;
        });
      })
      .catch(() => setModels([]));
    api
      .getSession(sessionId)
      .then((detail) => {
        const preferences = (detail.session as unknown as {
          preferences?: { llm_selection?: LLMSelection; knowledge_bases?: string[] | null };
        })?.preferences;
        if (preferences?.llm_selection?.model_id) setSelection(preferences.llm_selection);
        if (preferences && preferences.knowledge_bases != null) {
          setKbIds(preferences.knowledge_bases ?? []);
        }
        if (detail.session?.mode) setMode(normalizeMode(detail.session.mode));
        const history = (detail.messages as SessionMessage[])
          .filter(
            (message) =>
              message.content || message.data?.card || message.data?.grade || message.data?.quiz_set,
          )
          .map<Item>((message) => ({
            role: message.role,
            text: message.content,
            ...(message.data?.card ? { card: message.data.card } : {}),
            ...(message.data?.grade ? { grade: message.data.grade } : {}),
            ...(message.data?.quiz_set ? { quiz: message.data.quiz_set } : {}),
          }));
        if (history.length) setItems(history);
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  // Until a session override exists, follow the path's default knowledge bases.
  useEffect(() => {
    setKbIds((current) =>
      current.length ? current : state?.knowledge_bases?.map((kb) => kb.id) ?? [],
    );
  }, [state?.knowledge_bases]);

  const changeKnowledgeBases = useCallback(
    (ids: string[]) => {
      setKbIds(ids);
      void api
        .patchSession(pathId, sessionId, { preferences: { knowledge_bases: ids } })
        .catch(() => undefined);
    },
    [pathId, sessionId],
  );

  const changeModel = useCallback(
    (modelId: string) => {
      const model = models.find((item) => item.id === modelId);
      if (!model) return;
      const next = { provider_id: model.provider_id, model_id: model.id };
      setSelection(next);
      void api
        .patchSession(pathId, sessionId, { preferences: { llm_selection: next } })
        .catch(() => undefined);
    },
    [models, pathId, sessionId],
  );

  const load = useCallback(async () => {
    const next = await api.getState(pathId);
    setState((previous) => {
      if (previous) {
        const before = masteredIds(previous);
        const after = masteredIds(next);
        for (const kpId of after) {
          if (!before.has(kpId)) {
            setCelebration(kpId);
            break;
          }
        }
      }
      return next;
    });
    try {
      setAskHint((await api.suggestQuestion(pathId)).suggestion);
    } catch {
      /* suggestion is best-effort */
    }
  }, [pathId]);

  const describeGrade = useCallback(
    (outcome: GradeOutcome) => {
      const lines: string[] = [];
      if (outcome.result === "correct") lines.push(t("q.verdict.correct"));
      else if (outcome.result === "partial") lines.push(t("q.verdict.partial"));
      else if (outcome.result === "incorrect") lines.push(t("q.verdict.incorrect"));
      else lines.push(t("q.verdict.unknown"));
      const explanation = (outcome.explanation ?? "").trim();
      if (explanation) lines.push(explanation);
      if (outcome.gate_cleared) lines.push(t("q.mastered"));
      return lines.join("\n\n");
    },
    [t],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const changeMode = useCallback(
    async (next: Mode) => {
      setMode(next);
      try {
        await api.setMode(pathId, sessionId, next);
      } catch {
        /* admission errors surface on the next turn */
      }
    },
    [pathId, sessionId],
  );

  const send = useCallback(
    async (
      text: string,
      mastery?: {
        answer?: { question_id: string; text: string };
        skip?: { question_id: string };
      },
    ) => {
      setBusy(true);
      setHandoffs([]);
      setItems((previous) => [
        ...previous,
        { role: "user", text },
        { role: "assistant", text: "" },
      ]);
      let assistant = "";
      let reported = false;
      const appendAssistant = (delta: string) => {
        if (!delta) return;
        assistant += delta;
        setItems((previous) => {
          const next = [...previous];
          const last = next[next.length - 1];
          if (last?.role === "assistant") {
            next[next.length - 1] = { ...last, text: last.text + delta };
          }
          return next;
        });
      };
      const replaceAssistant = (value: string) => {
        assistant = value;
        setItems((previous) => {
          const next = [...previous];
          const last = next[next.length - 1];
          if (last?.role === "assistant") {
            next[next.length - 1] = { ...last, text: value };
          }
          return next;
        });
      };
      try {
        const practice = capability === "deep_question";
        await runTurn(
          {
            session_id: sessionId,
            path_id: pathId,
            mode,
            message: text,
            llm_selection: selection ?? undefined,
            capability: practice ? "deep_question" : undefined,
            num_questions: practice ? 3 : undefined,
            mastery_answer: mastery?.answer,
            mastery_skip: mastery?.skip,
          },
          (event) => {
            if (event.type === "text_delta") {
              appendAssistant(String(event.data.text ?? ""));
            }
            if (event.type === "quiz_plan") {
              const plan = event.data as unknown as QuizPlan;
              if (plan?.set_id) {
                setItems((previous) => {
                  const next = [...previous];
                  const last = next[next.length - 1];
                  const placeholder =
                    last?.role === "assistant" && !last.text && !last.card && !last.grade && !last.quiz;
                  const row: Item = {
                    role: "assistant",
                    text: "",
                    planSetId: plan.set_id,
                    planCount: plan.count,
                  };
                  if (placeholder) next[next.length - 1] = row;
                  else next.push(row);
                  return next;
                });
              }
            }
            if (event.type === "quiz_question") {
              const question = event.data as unknown as QuizQuestion & { set_id: string };
              if (question?.question_id && question.set_id) {
                const draft = questionDrafts.current.get(question.set_id) ?? [];
                if (!draft.some((item) => item.question_id === question.question_id)) {
                  draft.push(question);
                }
                questionDrafts.current.set(question.set_id, draft);
              }
            }
            if (event.type === "quiz_set") {
              const quiz = event.data as unknown as QuizSet;
              if (quiz?.set_id) {
                const draft = questionDrafts.current.get(quiz.set_id);
                const merged =
                  draft?.length === quiz.questions?.length
                    ? { ...quiz, questions: draft }
                    : quiz;
                setItems((previous) => [
                  ...previous.filter(
                    (item) => item.planSetId !== quiz.set_id && item.quiz?.set_id !== quiz.set_id,
                  ),
                  { role: "assistant", text: "", quiz: merged },
                ]);
              }
            }
            if (event.type === "question_card") {
              const card = event.data as unknown as PendingQuestion;
              if (card?.question_id) {
                setItems((previous) =>
                  previous.some((item) => item.card?.question_id === card.question_id)
                    ? previous
                    : [...previous, { role: "assistant", text: "", card }],
                );
              }
            }
            if (event.type === "session_meta") {
              const next = event.data.mastery_session_mode;
              const nextMode = typeof next === "string" ? normalizeMode(next) : mode;
              if (typeof next === "string") setMode(nextMode);
              const nextPath = event.data.mastery_path_id;
              if (typeof nextPath === "string" && nextPath && nextPath !== pathId) {
                const target = masterySessionRoute(nextPath, nextMode, sessionId);
                router.replace(target);
              }
            }
            if (event.type === "tool_result") {
              const metadata = event.data.metadata as Record<string, unknown> | undefined;
              const grade = metadata?.mastery_grade as GradeOutcome | undefined;
              if (grade?.question_id) {
                setItems((previous) => {
                  if (previous.some((item) => item.grade?.question_id === grade.question_id)) {
                    return previous;
                  }
                  const next = [...previous];
                  const last = next[next.length - 1];
                  const row: Item = { role: "assistant", text: "", grade };
                  const placeholder =
                    last?.role === "assistant" && !last.text && !last.card && !last.grade && !last.quiz;
                  if (placeholder) next.splice(next.length - 1, 0, row);
                  else next.push(row);
                  return next;
                });
              }
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
            }
            if (event.type === "error") {
              const message = String(event.data.message ?? "");
              replaceAssistant(assistant ? `${assistant}\n\n${message}` : message);
              reported = true;
            }
          },
        );
      } catch (reason) {
        if (!reported) {
          const message = `${t("study.noProvider")} (${String(reason)})`;
          replaceAssistant(assistant ? `${assistant}\n\n${message}` : message);
        }
      }
      if (!assistant) {
        setItems((previous) => {
          const next = [...previous];
          const last = next[next.length - 1];
          if (last?.role === "assistant" && !last.text && !last.quiz && !last.card && !last.grade) {
            next.pop();
          }
          return next;
        });
      }
      if (capability === "deep_question") setCapability("mastery_path");
      await load().catch(() => undefined);
      setBusy(false);
    },
    [pathId, sessionId, mode, load, t, selection, capability],
  );

  /** 作答作为下一条 WS 消息提交；判分由服务端在导师开口前完成。 */
  const answerQuestion = useCallback(
    async (question: PendingQuestion, answer: string) => {
      await send(answer, {
        answer: { question_id: question.question_id, text: answer },
      });
    },
    [send],
  );

  /** 跳过同样作为一条消息；先本地移除题卡，服务端会清空槽位。 */
  const skipQuestion = useCallback(
    async (question: PendingQuestion) => {
      setItems((previous) =>
        previous.filter((item) => item.card?.question_id !== question.question_id),
      );
      await send("跳过这道题", { skip: { question_id: question.question_id } });
    },
    [send],
  );

  useEffect(() => {
    if (!state || pendingSent.current) return;
    pendingSent.current = true;
    const prompt = takePendingPrompt();
    if (prompt) void send(prompt);
  }, [state, send]);

  if (!state) {
    return <div className="flex h-full items-center justify-center text-muted-foreground">{t("common.loading")}</div>;
  }

  const { counts, next, pending_question } = state;
  const progress = counts.total ? counts.mastered / counts.total : 0;
  const note = MODE_NOTES[mode];
  const answeredCardIds = new Set(
    items
      .filter((item) => item.grade?.question_id && item.grade.status !== "ungraded")
      .map((item) => item.grade!.question_id as string),
  );
  const inlineCardIds = new Set(
    items.filter((item) => item.card).map((item) => item.card!.question_id),
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-3">
        <Link href={masteryTopicRoute(pathId)} aria-label={t("dash.back")} className="flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted/60">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[14px] font-semibold">{state.name || pathId}</div>
          <div className="truncate text-[12px] text-muted-foreground">{next.knowledge_point_name}</div>
        </div>
        <ModeSwitch mode={mode} onSelect={changeMode} disabled={busy} />
        <div className="flex items-center gap-1.5 pl-2">
          <ProgressRing value={progress} size={18} stroke={2} showLabel={false} />
          <span className="text-[12px] tabular-nums text-muted-foreground">
            {counts.mastered}/{counts.total}
          </span>
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className={`hidden shrink-0 overflow-y-auto border-r border-border bg-card/30 transition-[width] xl:block ${outlineOpen ? "w-[252px] px-5 py-5" : "w-9"}`}>
          <StudyOutline
            map={state.map}
            currentPointId={next.knowledge_point_id}
            justMasteredId={celebration}
            collapsed={!outlineOpen}
            onToggleCollapsed={() => setOutlineOpen((value) => !value)}
          />
        </aside>

        <section className="flex min-w-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-auto w-full max-w-3xl px-5 py-6">
              {note && (
                <p className="mb-5 rounded-lg border border-border bg-secondary px-3.5 py-2.5 text-[12px] text-muted-foreground">
                  {t(note)}
                </p>
              )}
              {items.length === 0 && !pending_question && handoffs.length === 0 ? (
                <div className="flex min-h-[45vh] flex-col items-center justify-center text-center">
                  <div className="text-[12px] text-muted-foreground">{t("dash.next")}</div>
                  <h2 className="mt-1.5 text-xl font-semibold">{next.knowledge_point_name || t("dash.celebrate")}</h2>
                  <p className="mt-2 max-w-lg text-sm text-muted-foreground">{t("dash.noOutline.body")}</p>
                </div>
              ) : (
                <div className="space-y-5">
                  {items.map((item, index) => {
                    if (item.planSetId && !item.text) {
                      return (
                        <div
                          key={`plan-${item.planSetId}`}
                          className="rounded-lg border border-border bg-secondary px-3.5 py-2.5 text-[12px] text-muted-foreground"
                        >
                          {t("quiz.plan", { count: item.planCount ?? 0 })}
                        </div>
                      );
                    }
                    if (item.quiz) {
                      return (
                        <QuizViewer
                          key={`quiz-${item.quiz.set_id}`}
                          pathId={pathId}
                          quizSet={item.quiz}
                        />
                      );
                    }
                    if (item.card) {
                      const card = item.card;
                      return (
                        <QuestionCard
                          key={`card-${card.question_id}`}
                          question={card}
                          answered={answeredCardIds.has(card.question_id)}
                          onAnswer={(answer) => void answerQuestion(card, answer)}
                          onSkip={() => void skipQuestion(card)}
                        />
                      );
                    }
                    if (item.grade) {
                      const content = item.grade.status === "ungraded"
                        ? `${t("q.ungraded")}${item.grade.reason ? ` (${item.grade.reason})` : ""}`
                        : describeGrade(item.grade);
                      return (
                        <MessageBubble
                          key={`grade-${index}`}
                          role="assistant"
                          content={content}
                          streaming={false}
                        />
                      );
                    }
                    return (
                      <MessageBubble
                        key={`message-${index}`}
                        role={item.role}
                        content={item.text}
                        streaming={busy && index === items.length - 1 && item.role === "assistant"}
                      />
                    );
                  })}
                  {handoffs.length > 0 && <MasteryHandoffCards handoffs={handoffs} />}
                  {pending_question && !inlineCardIds.has(pending_question.question_id) && (
                    <QuestionCard
                      question={pending_question}
                      answered={answeredCardIds.has(pending_question.question_id)}
                      onAnswer={(answer) => void answerQuestion(pending_question, answer)}
                      onSkip={() => void skipQuestion(pending_question)}
                    />
                  )}
                </div>
              )}
              <div ref={bottomRef} />
            </div>
          </div>
          <MessageComposer
            models={models}
            modelId={selection?.model_id ?? ""}
            onModelChange={changeModel}
            knowledgeBases={knowledgeBases}
            kbIds={kbIds}
            onKbChange={changeKnowledgeBases}
            streaming={busy}
            onSend={(text) => void send(text)}
            placeholder={t("study.placeholder")}
            askHint={askHint}
            capabilities={[
              { id: "mastery_path", label: t("quiz.capability.mastery") },
              { id: "deep_question", label: t("quiz.capability.practice") },
            ]}
            capabilityId={capability}
            onCapabilityChange={(id) =>
              setCapability(id === "deep_question" ? "deep_question" : "mastery_path")
            }
          />
        </section>
      </div>

      {celebration && <LevelUpCelebration onDone={() => setCelebration(null)} />}
    </div>
  );
}

export default function MasteryStudyPage() {
  return (
    <Suspense fallback={<div className="flex h-full items-center justify-center text-muted-foreground">…</div>}>
      <StudyRoute />
    </Suspense>
  );
}
