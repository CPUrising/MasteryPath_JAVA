"use client";

import Link from "next/link";

import { masteryTopicRoute } from "@/lib/learning-routes";

export interface TopicCardData {
  path_id: string;
  name: string;
  goal: string;
  objectives: number;
  mastered?: number;
  due_reviews?: number;
}

export function TopicMapCard({ topic }: { topic: TopicCardData }) {
  return (
    <Link
      href={masteryTopicRoute(topic.path_id)}
      className="block rounded-xl border border-border bg-card p-5 transition hover:-translate-y-0.5 hover:border-primary/35"
    >
      <h3 className="text-[15px] font-semibold">{topic.name || topic.path_id}</h3>
      <p className="mt-1 line-clamp-2 text-[13px] text-muted-foreground">
        {topic.goal || "—"}
      </p>
      <div className="mt-4 flex items-center gap-2 text-[12px] text-muted-foreground">
        <span>{topic.objectives} KP</span>
        {typeof topic.mastered === "number" && (
          <span>
            {topic.mastered}/{topic.objectives} mastered
          </span>
        )}
        {(topic.due_reviews ?? 0) > 0 && (
          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-primary">
            {topic.due_reviews} due
          </span>
        )}
      </div>
    </Link>
  );
}
