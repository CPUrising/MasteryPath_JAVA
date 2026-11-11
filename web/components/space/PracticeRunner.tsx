"use client";

import { useState } from "react";
import { ArrowRight, CircleSlash, X, XCircle, CheckCircle2 } from "lucide-react";
import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "@/components/common/MarkdownMessage";
import { api } from "@/lib/api";
import type { PracticeCard, QuestionAttemptOutcome } from "@/lib/types";

function verdictKey(result?: string): string {
  if (result === "correct") return "q.verdict.correct";
  if (result === "partial") return "q.verdict.partial";
  if (result === "incorrect") return "q.verdict.incorrect";
  return "q.verdict.unknown";
}

/** 题库刷题器：逐题作答、服务端判分、即时复盘。不写掌握闭环。 */
export function PracticeRunner({
  cards,
  onExit,
  onRestart,
  onAnswered,
}: {
  cards: PracticeCard[];
  onExit: () => void;
  onRestart: () => void;
  onAnswered: () => void;
}) {
  const { t } = useTranslation();
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [outcome, setOutcome] = useState<QuestionAttemptOutcome | null>(null);
  const [correctCount, setCorrectCount] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [finished, setFinished] = useState(cards.length === 0);

  if (finished || cards.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-5">
        <h3 className="text-[15px] font-semibold">{t("practice.title")}</h3>
        {cards.length === 0 ? (
          <p className="mt-2 text-[13px] text-muted-foreground">{t("practice.empty")}</p>
        ) : (
          <p className="mt-2 text-[14px]">
            {t("practice.summary", { correct: correctCount, total: cards.length })}
          </p>
        )}
        <div className="mt-4 flex items-center gap-2">
          {cards.length > 0 && (
            <button
              type="button"
              onClick={onRestart}
              className="h-8 rounded-full bg-primary px-3.5 text-[13px] font-medium text-primary-foreground"
            >
              {t("practice.again")}
            </button>
          )}
          <button
            type="button"
            onClick={onExit}
            className="h-8 rounded-full border border-border px-3.5 text-[13px]"
          >
            {t("practice.exit")}
          </button>
        </div>
      </div>
    );
  }

  const current = cards[index];
  const graded = outcome?.status === "graded";
  const submit = async () => {
    if (submitting || graded || !answer.trim()) return;
    setSubmitting(true);
    setError("");
    try {
      const result = await api.attemptQuestion(current.entry_id, answer.trim());
      setOutcome(result);
      if (result.status === "graded") {
        if (result.is_correct) setCorrectCount((count) => count + 1);
        onAnswered();
      }
    } catch (reason) {
      setError(`${t("practice.error")} (${String(reason)})`);
    } finally {
      setSubmitting(false);
    }
  };

  const next = () => {
    if (index + 1 >= cards.length) {
      setFinished(true);
      return;
    }
    setIndex(index + 1);
    setAnswer("");
    setOutcome(null);
    setError("");
  };

  return (
    <div className="rounded-xl border border-primary/25 bg-primary/[0.04] p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[11px] uppercase tracking-wide text-muted-foreground">
            {t("practice.title")}
          </div>
          <div className="mt-0.5 text-[13px] font-medium">
            {t("practice.progress", { index: index + 1, total: cards.length })}
          </div>
          <div className="mt-0.5 truncate text-[11px] text-muted-foreground">
            {current.path_name || current.path_id}
            {current.knowledge_point_name ? ` · ${current.knowledge_point_name}` : ""}
          </div>
        </div>
        <button
          type="button"
          onClick={onExit}
          aria-label={t("practice.exit")}
          className="shrink-0 text-muted-foreground hover:text-foreground"
        >
          <X size={15} />
        </button>
      </div>

      <MarkdownMessage content={current.question} className="mt-3 text-[14px] font-medium" />

      {current.options.length > 0 && current.question_type === "choice" ? (
        <ul className="mt-3 space-y-1.5">
          {current.options.map((option) => {
            const active = answer === option.label;
            const revealCorrect =
              graded && outcome?.correct_answer === option.label;
            const revealWrong = graded && active && !outcome?.is_correct;
            return (
              <li key={option.label}>
                <button
                  type="button"
                  disabled={graded}
                  onClick={() => setAnswer(option.label)}
                  className={`w-full rounded-lg border px-3 py-2 text-left text-[13px] disabled:cursor-default ${
                    revealCorrect
                      ? "border-primary/60 bg-primary/10"
                      : revealWrong
                        ? "border-destructive/50 bg-destructive/10"
                        : active
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
            value={answer}
            disabled={graded}
            placeholder={t("q.placeholder")}
            onChange={(event) => setAnswer(event.target.value)}
            className="max-h-32 w-full resize-none bg-transparent px-2 py-1.5 text-[14px] text-foreground outline-none placeholder:text-muted-foreground disabled:opacity-70"
          />
        </div>
      )}

      {outcome && (
        <div className="mt-3 space-y-1.5 text-[13px]">
          <p className="inline-flex items-center gap-1.5 font-medium">
            {outcome.status === "graded" ? (
              outcome.is_correct ? (
                <CheckCircle2 size={14} className="text-primary" />
              ) : (
                <XCircle size={14} className="text-destructive" />
              )
            ) : (
              <CircleSlash size={14} className="text-amber-600" />
            )}
            {outcome.status === "graded" ? t(verdictKey(outcome.result)) : outcome.reason}
          </p>
          {graded && outcome.correct_answer && (
            <p>
              <span className="text-muted-foreground">{t("quiz.correctAnswer")}：</span>
              {outcome.correct_answer}
            </p>
          )}
          {graded && outcome.explanation && (
            <p className="text-muted-foreground">{outcome.explanation}</p>
          )}
        </div>
      )}

      {error && <p className="mt-2 text-[12px] text-destructive">{error}</p>}

      <div className="mt-3 flex items-center justify-end gap-2">
        {!graded ? (
          <button
            type="button"
            disabled={submitting || !answer.trim()}
            onClick={() => void submit()}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-primary px-3.5 text-[13px] font-medium text-primary-foreground disabled:opacity-40"
          >
            {submitting ? t("quiz.submitting") : t("practice.submit")}
          </button>
        ) : (
          <button
            type="button"
            onClick={next}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-primary px-3.5 text-[13px] font-medium text-primary-foreground"
          >
            <ArrowRight size={14} />
            {t("practice.next")}
          </button>
        )}
      </div>
    </div>
  );
}
