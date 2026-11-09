"use client";

import { useEffect, useRef, useState } from "react";
import { Check, Copy, Undo2 } from "lucide-react";
import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "./MarkdownMessage";

/**
 * One conversation bubble, shared by general chat and the mastery tutor.
 *
 * User text stays pre-wrapped; assistant text is Markdown. Copy is always
 * available; withdraw is only rendered when the caller can persist a delete.
 */
export function MessageBubble({
  role,
  content,
  attachments = [],
  streaming = false,
  onWithdraw,
}: {
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string }[];
  streaming?: boolean;
  onWithdraw?: () => void;
}) {
  const { t } = useTranslation();
  const user = role === "user";
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopied(false), 1600);
    } catch {
      /* clipboard may be unavailable on insecure origins */
    }
  };

  return (
    <div className={`group flex flex-col ${user ? "items-end" : "items-start"}`}>
      <div
        className={`max-w-[85%] rounded-xl px-3.5 py-2.5 text-sm ${
          user ? "bg-primary/10" : "bg-muted"
        }`}
      >
        {user ? (
          <div className="whitespace-pre-wrap leading-relaxed">{content}</div>
        ) : content ? (
          <MarkdownMessage content={content} />
        ) : streaming ? (
          <span className="inline-block h-4 w-1.5 animate-pulse rounded-full bg-muted-foreground/60" />
        ) : null}
        {attachments.length > 0 && (
          <span className="mt-1 block text-[11px] text-muted-foreground">
            {attachments.map((item) => item.name).join(", ")}
          </span>
        )}
      </div>

      <div className="mt-1 flex items-center gap-0.5 text-[11px] text-muted-foreground opacity-100 transition md:opacity-0 md:group-hover:opacity-100 md:group-focus-within:opacity-100">
        <button
          type="button"
          title={copied ? t("chat.copied") : t("chat.copy")}
          aria-label={t("chat.copy")}
          disabled={!content}
          onClick={() => void copy()}
          className="inline-flex h-6 items-center gap-1 rounded-md px-1.5 hover:bg-accent disabled:opacity-40"
        >
          {copied ? <Check size={12} /> : <Copy size={12} />}
          <span>{copied ? t("chat.copied") : t("chat.copy")}</span>
        </button>
        {user && onWithdraw && (
          <button
            type="button"
            title={t("chat.withdraw")}
            aria-label={t("chat.withdraw")}
            onClick={onWithdraw}
            className="inline-flex h-6 items-center gap-1 rounded-md px-1.5 hover:bg-accent"
          >
            <Undo2 size={12} />
            <span>{t("chat.withdraw")}</span>
          </button>
        )}
      </div>
    </div>
  );
}
