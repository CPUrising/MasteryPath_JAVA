"use client";

import { useEffect, useRef } from "react";

import { MessageBubble } from "@/components/common/MessageBubble";

export interface DisplayMessage {
  id: string | number;
  role: "user" | "assistant";
  content: string;
  attachments: { name: string }[];
  /** Server-side message id; absent for optimistic messages that are not synced yet. */
  serverId?: number;
}

/** Message list for the general chat surface. */
export function ChatMessageList({
  messages,
  streaming,
  onWithdraw,
}: {
  messages: DisplayMessage[];
  streaming: boolean;
  onWithdraw?: (message: DisplayMessage) => void;
}) {
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    bottom.current?.scrollIntoView({ block: "end" });
  }, [messages, streaming]);

  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 px-4 py-6">
      {messages.map((message, index) => (
        <MessageBubble
          key={`${message.id}-${message.role}`}
          role={message.role}
          content={message.content}
          attachments={message.attachments}
          streaming={streaming && index === messages.length - 1}
          onWithdraw={
            message.role === "user" && onWithdraw && !streaming
              ? () => onWithdraw(message)
              : undefined
          }
        />
      ))}
      <div ref={bottom} />
    </div>
  );
}
