"use client";

import type { ReactNode } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * Renders assistant replies as GitHub-flavoured Markdown (code, lists, tables, links).
 *
 * `inline` swaps the block container for a `<span>` and drops the `<p>` wrapper so the
 * output can sit inside a button, such as a quiz option.
 */
export function MarkdownMessage({
  content,
  className,
  inline = false,
}: {
  content: string;
  className?: string;
  inline?: boolean;
}) {
  const components: Components = {
    a: ({ children, ...props }) => (
      <a {...props} target="_blank" rel="noreferrer">
        {children}
      </a>
    ),
  };
  if (inline) {
    components.p = ({ children }: { children?: ReactNode }) => <>{children}</>;
  }

  const classes = ["chat-markdown", inline ? "" : "text-[14px] leading-relaxed", className ?? ""]
    .filter(Boolean)
    .join(" ");

  const body = (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
      {content}
    </ReactMarkdown>
  );

  return inline ? <span className={classes}>{body}</span> : <div className={classes}>{body}</div>;
}
