"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { GraduationCap, RotateCcw, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";

import {
  masteryHandoffHref,
  type MasteryHandoffPayload,
} from "@/lib/mastery-handoff";
import { setPendingPrompt } from "@/lib/pending-prompt";

export function MasteryHandoffCard({ data }: { data: MasteryHandoffPayload }) {
  const { t } = useTranslation();
  const router = useRouter();
  const [opening, setOpening] = useState(data.opening_message);
  const resuming = data.kind === "open";
  const headline =
    data.reason ||
    t(resuming ? "handoff.resume" : "handoff.start", {
      defaultValue: resuming ? "Pick this back up" : "Start a session on this path",
    });

  const go = () => {
    if (opening.trim()) setPendingPrompt(opening);
    router.push(masteryHandoffHref(data));
  };

  return (
    <div className="rounded-2xl border border-border bg-card p-4 text-foreground shadow-sm">
      <div className="flex items-start gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
          {resuming ? <RotateCcw size={16} /> : <Sparkles size={16} />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-medium leading-snug">{headline}</div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
            <GraduationCap size={12} />
            <span className="truncate">{data.path_name || data.path_id}</span>
            {data.module_name ? (
              <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
                {data.module_name}
              </span>
            ) : null}
          </div>
          {(data.objectives > 0 || data.due_reviews > 0) && (
            <div className="mt-1.5 flex flex-wrap gap-2 text-[11px] text-muted-foreground">
              {data.objectives > 0 && (
                <span>
                  {data.mastered}/{data.objectives} {t("handoff.mastered", { defaultValue: "mastered" })}
                </span>
              )}
              {data.due_reviews > 0 && (
                <span>
                  {data.due_reviews} {t("handoff.due", { defaultValue: "due for review" })}
                </span>
              )}
            </div>
          )}
          {opening || data.opening_message ? (
            <label className="mt-3 block">
              <span className="text-[10.5px] text-muted-foreground">
                {t("handoff.opensWith", { defaultValue: "Opens with" })}
              </span>
              <textarea
                value={opening}
                onChange={(event) => setOpening(event.target.value)}
                rows={2}
                className="mt-1 w-full resize-none rounded-xl border border-border bg-background px-2.5 py-2 text-[12px] outline-none focus:border-primary"
              />
            </label>
          ) : null}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={go}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary px-3 py-1.5 text-[11.5px] font-medium text-primary-foreground hover:opacity-90"
            >
              {resuming ? <RotateCcw size={12} /> : <Sparkles size={12} />}
              {t(resuming ? "handoff.continue" : "handoff.begin", {
                defaultValue: resuming ? "Continue this session" : "Begin a session",
              })}
            </button>
            {resuming && data.session_title ? (
              <span className="truncate text-[11px] text-muted-foreground">
                {data.session_title}
                {data.session_awaiting
                  ? ` · ${t("handoff.waiting", { defaultValue: "a question is waiting" })}`
                  : ""}
              </span>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}

export function MasteryHandoffCards({ handoffs }: { handoffs: MasteryHandoffPayload[] }) {
  if (!handoffs.length) return null;
  return (
    <div className="space-y-3">
      {handoffs.map((item) => (
        <MasteryHandoffCard
          key={`${item.kind}:${item.path_id}:${item.session_id}:${item.module_id}`}
          data={item}
        />
      ))}
    </div>
  );
}
