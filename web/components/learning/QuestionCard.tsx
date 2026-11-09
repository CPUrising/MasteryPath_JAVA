"use client";

import { useState } from "react";
import { ArrowUp } from "lucide-react";
import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "@/components/common/MarkdownMessage";
import type { PendingQuestion } from "@/lib/types";

/** A posed question card. Answering starts the next turn. */
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
  const [text, setText] = useState("");

  const submit = () => {
    const value = text.trim();
    if (!value) return;
    onAnswer(value);
    setText("");
  };

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
        <div className="mt-3 rounded-2xl border border-border bg-card p-2 shadow-sm transition focus-within:border-primary/40">
          <textarea
            rows={1}
            value={text}
            placeholder={t("q.placeholder")}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit();
              }
            }}
            className="max-h-32 w-full resize-none bg-transparent px-2 py-1.5 text-[14px] text-foreground outline-none placeholder:text-muted-foreground"
          />
          <div className="flex justify-end">
            <button
              type="button"
              aria-label={t("q.submit")}
              title={t("q.submit")}
              disabled={!text.trim()}
              onClick={submit}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary text-primary-foreground disabled:opacity-40"
            >
              <ArrowUp size={16} />
            </button>
          </div>
        </div>
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
