"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowUp, CircleHelp, XCircle } from "lucide-react";
import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "@/components/common/MarkdownMessage";
import { api } from "@/lib/api";
import type { QuizQuestion, QuizSet } from "@/lib/types";

function initialAnswers(questions: QuizQuestion[]): Record<string, string> {
  const answers: Record<string, string> = {};
  for (const question of questions) {
    if (question.learner_answer) answers[question.question_id] = question.learner_answer;
  }
  return answers;
}

function verdictKey(result?: string): string {
  if (result === "correct") return "q.verdict.correct";
  if (result === "partial") return "q.verdict.partial";
  return "q.verdict.incorrect";
}

/** 生成型练习题卷：逐题作答、整卷提交、提交后复盘。 */
export function QuizViewer({ pathId, quizSet }: { pathId: string; quizSet: QuizSet }) {
  const { t } = useTranslation();
  const [view, setView] = useState<QuizSet>(quizSet);
  const [answers, setAnswers] = useState<Record<string, string>>(() =>
    initialAnswers(quizSet.questions),
  );
  const [submitting, setSubmitting] = useState(false);
  const [reasons, setReasons] = useState<string[]>([]);
  const [error, setError] = useState("");
  const hydrated = useRef("");

  useEffect(() => {
    setView(quizSet);
    setAnswers(initialAnswers(quizSet.questions));
    setReasons([]);
    setError("");
  }, [quizSet]);

  // 刷新恢复：未提交题卷以服务端为准；已提交题卷由服务端释放答案。
  useEffect(() => {
    const setId = quizSet.set_id;
    if (!setId || quizSet.status === "submitted" || hydrated.current === setId) return;
    hydrated.current = setId;
    void api
      .getQuizSet(pathId, setId)
      .then((fresh) => {
        setView(fresh);
        setAnswers(initialAnswers(fresh.questions));
      })
      .catch(() => undefined);
  }, [pathId, quizSet.set_id, quizSet.status]);

  const submitted = view.status === "submitted";
  const missing = view.questions.filter(
    (question) => !(answers[question.question_id] ?? "").trim(),
  ).length;

  const submit = async () => {
    if (submitted || submitting || missing > 0) return;
    setSubmitting(true);
    setError("");
    try {
      const outcome = await api.submitQuizSet(
        pathId,
        view.set_id,
        view.questions.map((question) => ({
          question_id: question.question_id,
          answer: (answers[question.question_id] ?? "").trim(),
        })),
      );
      if (outcome.status === "ungraded") {
        setReasons(outcome.ungraded_reasons ?? []);
      } else {
        setReasons([]);
        setView(outcome);
        setAnswers(initialAnswers(outcome.questions));
      }
    } catch (reason) {
      setError(`${t("quiz.submitFailed")} (${String(reason)})`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="rounded-xl border border-primary/25 bg-primary/[0.04] p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[11px] uppercase tracking-wide text-muted-foreground">
            {t("quiz.title")}
          </div>
          <h3 className="mt-0.5 truncate text-[15px] font-semibold">
            {view.title || view.topic || t("quiz.title")}
          </h3>
        </div>
        {submitted && (
          <span className="shrink-0 rounded-full bg-primary/10 px-2.5 py-1 text-[12px] font-medium text-primary">
            {t("quiz.score", { score: Math.round((view.score ?? 0) * 100) })}
          </span>
        )}
      </div>

      <ol className="mt-4 space-y-4">
        {view.questions.map((question) => {
          const value = answers[question.question_id] ?? "";
          const setValue = (answer: string) =>
            setAnswers((current) => ({ ...current, [question.question_id]: answer }));
          return (
            <li key={question.question_id} className="rounded-lg border border-border bg-card p-3.5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-[12px] font-medium text-muted-foreground">
                  {t("quiz.question", { seq: question.seq })}
                </span>
                <span className="flex items-center gap-2">
                  {question.difficulty && (
                    <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                      {question.difficulty}
                    </span>
                  )}
                  {submitted && (
                    <span
                      className={`inline-flex items-center gap-1 text-[11px] ${
                        question.is_correct ? "text-primary" : "text-destructive"
                      }`}
                    >
                      {question.is_correct ? <CircleHelp size={12} /> : <XCircle size={12} />}
                      {t(verdictKey(question.result))}
                    </span>
                  )}
                </span>
              </div>

              <MarkdownMessage content={question.prompt} className="mt-1.5 text-sm font-medium" />

              {submitted ? (
                <div className="mt-3 space-y-2 text-[13px]">
                  <p>
                    <span className="text-muted-foreground">{t("quiz.yourAnswer")}：</span>
                    {question.learner_answer || "—"}
                  </p>
                  {question.correct_answer && (
                    <p>
                      <span className="text-muted-foreground">{t("quiz.correctAnswer")}：</span>
                      {question.correct_answer}
                    </p>
                  )}
                  {question.explanation && (
                    <p className="text-muted-foreground">{question.explanation}</p>
                  )}
                </div>
              ) : question.options.length > 0 ? (
                <ul className="mt-3 space-y-1.5">
                  {question.options.map((option) => {
                    const active = value === option.label;
                    return (
                      <li key={option.label}>
                        <button
                          type="button"
                          onClick={() => setValue(option.label)}
                          className={`w-full rounded-lg border px-3 py-2 text-left text-[13px] ${
                            active
                              ? "border-primary/60 bg-primary/10"
                              : "border-border bg-background hover:border-primary/40"
                          }`}
                        >
                          <strong className="mr-2">{option.label}.</strong>
                          <MarkdownMessage content={option.body} inline className="text-[13px]" />
                        </button>
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <div className="mt-3 rounded-2xl border border-border bg-background p-2 shadow-sm focus-within:border-primary/40">
                  <textarea
                    rows={2}
                    value={value}
                    placeholder={t("q.placeholder")}
                    onChange={(event) => setValue(event.target.value)}
                    className="max-h-32 w-full resize-none bg-transparent px-2 py-1.5 text-[14px] text-foreground outline-none placeholder:text-muted-foreground"
                  />
                </div>
              )}
            </li>
          );
        })}
      </ol>

      {!submitted && (
        <div className="mt-3 space-y-2">
          {reasons.length > 0 && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-[12px] text-amber-700 dark:text-amber-400">
              <p className="font-medium">{t("quiz.ungraded")}</p>
              <ul className="mt-1 list-disc pl-4">
                {reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            </div>
          )}
          {error && <p className="text-[12px] text-destructive">{error}</p>}
          <div className="flex items-center justify-end gap-3">
            {missing > 0 && (
              <span className="text-[12px] text-muted-foreground">
                {t("quiz.unanswered", { count: missing })}
              </span>
            )}
            <button
              type="button"
              disabled={submitting || missing > 0}
              onClick={() => void submit()}
              className="inline-flex h-8 items-center gap-1.5 rounded-full bg-primary px-3.5 text-[13px] font-medium text-primary-foreground disabled:opacity-40"
            >
              <ArrowUp size={14} />
              {submitting ? t("quiz.submitting") : t("quiz.submit")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
