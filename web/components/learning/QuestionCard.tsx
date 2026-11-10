"use client";

import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "@/components/common/MarkdownMessage";
import type { PendingQuestion } from "@/lib/types";

/** A posed question card. Choice cards answer in place; free text uses the composer. */
export function QuestionCard({
  question,
  onAnswer,
  onSkip,
  answered = false,
}: {
  question: PendingQuestion;
  onAnswer: (text: string) => void;
  onSkip: () => void;
  answered?: boolean;
}) {
  const { t } = useTranslation();

  return (
    <div className="rounded-xl border border-primary/25 bg-primary/[0.05] p-4">
      {question.objective_name && (
        <div className="mb-1 text-[11px] uppercase tracking-wide text-muted-foreground">
          {question.objective_name}
        </div>
      )}
      <MarkdownMessage content={question.prompt} className="text-sm font-medium" />
      {answered ? (
        <p className="mt-3 text-[12px] text-muted-foreground">{t("q.answered")}</p>
      ) : question.options.length > 0 ? (
        <ul className="mt-3 space-y-1.5">
          {question.options.map((option) => (
            <li key={option.label}>
              <button
                type="button"
                onClick={() => onAnswer(option.label)}
                className="w-full rounded-lg border border-border bg-card px-3 py-2 text-left text-[13px] hover:border-primary/40"
              >
                <strong className="mr-2">{option.label}.</strong>
                <MarkdownMessage content={option.body} inline className="text-[13px]" />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-[12px] text-muted-foreground">{t("q.answerBelow")}</p>
      )}
      {!answered && (
        <div className="mt-3 text-right">
          <button type="button" onClick={onSkip} className="text-[12px] text-muted-foreground hover:underline">
            {t("q.skip")}
          </button>
        </div>
      )}
    </div>
  );
}
