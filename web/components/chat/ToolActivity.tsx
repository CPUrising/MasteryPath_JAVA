"use client";

import { Check, ChevronRight, Loader2, Wrench, X } from "lucide-react";
import { useTranslation } from "react-i18next";

import { summarizeArguments } from "@/lib/tool-activity";
import type { ToolActivityEntry, ToolActivityStatus } from "@/lib/types";

/** 一行一个工具动作，点击展开参数与返回结果。 */
export function ToolActivityList({ activities }: { activities?: ToolActivityEntry[] }) {
  const { t } = useTranslation();
  if (!activities || activities.length === 0) return null;

  return (
    <div className="mb-1.5 w-full max-w-[85%] space-y-1 rounded-lg border border-border bg-card/60 p-1.5">
      {activities.map((item) => {
        const summary = summarizeArguments(item.arguments);
        const empty = Object.keys(item.arguments ?? {}).length === 0;
        return (
          <details key={item.id} className="group rounded-md">
            <summary className="flex cursor-pointer list-none items-center gap-1.5 rounded-md px-1.5 py-1 text-[11px] hover:bg-muted/60 [&::-webkit-details-marker]:hidden">
              <ChevronRight
                size={12}
                className="shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
              />
              <StatusIcon status={item.status} />
              <Wrench size={11} className="shrink-0 text-muted-foreground" />
              <code className="shrink-0 font-mono">{item.name}</code>
              {summary && (
                <span className="min-w-0 flex-1 truncate text-muted-foreground">{summary}</span>
              )}
              <span className="ml-auto shrink-0 text-muted-foreground">
                {t(`tool.status.${item.status}`)}
              </span>
            </summary>
            <div className="space-y-1 px-6 pb-1.5 pt-0.5">
              <div className="text-[10px] uppercase tracking-wide text-muted-foreground">
                {t("tool.arguments")}
              </div>
              <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all rounded bg-muted/60 p-2 text-[11px]">
                {empty ? t("tool.emptyArguments") : JSON.stringify(item.arguments, null, 2)}
              </pre>
              {item.content !== undefined && item.content !== "" && (
                <>
                  <div className="text-[10px] uppercase tracking-wide text-muted-foreground">
                    {t("tool.result")}
                  </div>
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all rounded bg-muted/60 p-2 text-[11px]">
                    {item.content}
                  </pre>
                </>
              )}
            </div>
          </details>
        );
      })}
    </div>
  );
}

function StatusIcon({ status }: { status: ToolActivityStatus }) {
  if (status === "running") {
    return <Loader2 size={12} className="shrink-0 animate-spin text-muted-foreground" />;
  }
  if (status === "failed") {
    return <X size={12} className="shrink-0 text-destructive" />;
  }
  return <Check size={12} className="shrink-0 text-primary" />;
}
