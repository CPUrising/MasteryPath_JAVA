"use client";

import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, Plus, Tags, XCircle } from "lucide-react";
import { useTranslation } from "react-i18next";

import { MarkdownMessage } from "@/components/common/MarkdownMessage";
import { api } from "@/lib/api";
import type {
  PracticeCard,
  QuestionEntry,
  QuestionKbGroup,
  QuestionTagSummary,
} from "@/lib/types";

import { PracticeRunner } from "./PracticeRunner";
import { TagManagerDialog } from "./TagManagerDialog";

type ResultFilter = "all" | "wrong" | "correct";

/** 题库工作台：检索、单条/批量标签、全局标签管理、刷题。 */
export function QuestionBankPanel() {
  const { t } = useTranslation();
  const [entries, setEntries] = useState<QuestionEntry[]>([]);
  const [tagSummary, setTagSummary] = useState<QuestionTagSummary[]>([]);
  const [kbGroups, setKbGroups] = useState<QuestionKbGroup[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<string[]>([]);
  const [onlyUnassigned, setOnlyUnassigned] = useState(false);
  const [resultFilter, setResultFilter] = useState<ResultFilter>("all");
  const [keyword, setKeyword] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [editingId, setEditingId] = useState("");
  const [tagDraft, setTagDraft] = useState("");
  const [batchMode, setBatchMode] = useState<"add" | "remove" | null>(null);
  const [batchDraft, setBatchDraft] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [tagDialogOpen, setTagDialogOpen] = useState(false);
  const [practice, setPractice] = useState<{ cards: PracticeCard[]; key: number } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [rows, tags, groups] = await Promise.all([
        api.searchQuestions({
          tags: selectedTags,
          knowledgeBaseIds: selectedKbIds,
          result: resultFilter === "all" ? undefined : resultFilter,
          q: keyword.trim() || undefined,
          limit: 300,
        }),
        api.questionTags(),
        api.questionKbGroups(),
      ]);
      setEntries(rows);
      setTagSummary(tags);
      setKbGroups(groups);
    } catch (reason) {
      setError(String(reason));
    } finally {
      setLoading(false);
    }
  }, [keyword, resultFilter, selectedKbIds, selectedTags]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 250);
    return () => clearTimeout(timer);
  }, [load]);

  const toggleTag = (tag: string) =>
    setSelectedTags((current) =>
      current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag],
    );

  const toggleSelected = (entryId: string) =>
    setSelectedIds((current) =>
      current.includes(entryId)
        ? current.filter((item) => item !== entryId)
        : [...current, entryId],
    );

  const saveTags = async (entry: QuestionEntry, tags: string[]) => {
    try {
      await api.saveQuestionTags(entry.entry_id, tags);
      await load();
    } catch (reason) {
      setError(String(reason));
    }
  };

  const commitDraftTag = async (entry: QuestionEntry) => {
    const tag = tagDraft.trim();
    setEditingId("");
    setTagDraft("");
    if (!tag || entry.tags.includes(tag)) return;
    await saveTags(entry, [...entry.tags, tag]);
  };

  const applyBatch = async () => {
    const tag = batchDraft.trim();
    if (!tag || selectedIds.length === 0) return;
    try {
      await api.batchTags(
        selectedIds,
        batchMode === "add" ? [tag] : [],
        batchMode === "remove" ? [tag] : [],
      );
      setSelectedIds([]);
      setBatchMode(null);
      setBatchDraft("");
      await load();
    } catch (reason) {
      setError(String(reason));
    }
  };

  const startPractice = async () => {
    setError("");
    try {
      const cards = await api.practiceQuestions({
        tags: selectedTags,
        knowledgeBaseIds: selectedKbIds,
        size: 10,
        order: resultFilter === "wrong" ? "wrong_first" : "newest",
      });
      setPractice({ cards, key: Date.now() });
    } catch (reason) {
      setError(String(reason));
    }
  };

  const toggleKb = (kbId: string) => {
    setOnlyUnassigned(false);
    setSelectedKbIds((current) =>
      current.includes(kbId) ? current.filter((item) => item !== kbId) : [...current, kbId],
    );
  };

  const visibleEntries = onlyUnassigned
    ? entries.filter((entry) => (entry.knowledge_bases ?? []).length === 0)
    : entries;

  if (practice) {
    return (
      <PracticeRunner
        key={practice.key}
        cards={practice.cards}
        onExit={() => setPractice(null)}
        onRestart={() => void startPractice()}
        onAnswered={() => void load()}
      />
    );
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder={t("space.bank.search")}
          className="h-9 min-w-[220px] flex-1 rounded-lg border border-border bg-background px-3 text-[13px] outline-none focus:border-primary/40"
        />
        <select
          value={resultFilter}
          onChange={(event) => setResultFilter(event.target.value as ResultFilter)}
          className="h-9 rounded-lg border border-border bg-background px-2 text-[13px]"
        >
          <option value="all">{t("space.bank.result.all")}</option>
          <option value="wrong">{t("space.bank.result.wrong")}</option>
          <option value="correct">{t("space.bank.result.correct")}</option>
        </select>
        <button
          type="button"
          onClick={() => void startPractice()}
          className="h-9 rounded-lg bg-primary px-3.5 text-[13px] font-medium text-primary-foreground"
        >
          {t("space.bank.practice")}
        </button>
        <button
          type="button"
          onClick={() => setTagDialogOpen(true)}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-border px-3.5 text-[13px]"
        >
          <Tags size={14} />
          {t("space.bank.manageTags")}
        </button>
      </div>

      {kbGroups.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-[11px] uppercase tracking-wide text-muted-foreground">
            {t("space.bank.kbFilter")}
          </span>
          {kbGroups.map((group) => {
            const unassigned = group.knowledge_base_id === "";
            const active = unassigned
              ? onlyUnassigned
              : selectedKbIds.includes(group.knowledge_base_id);
            const label = unassigned ? t("space.bank.unassignedKb") : group.name;
            return (
              <button
                key={group.knowledge_base_id || "__unassigned__"}
                type="button"
                onClick={() => {
                  if (unassigned) {
                    setSelectedKbIds([]);
                    setOnlyUnassigned((current) => !current);
                  } else {
                    toggleKb(group.knowledge_base_id);
                  }
                }}
                className={`rounded-full border px-2.5 py-0.5 text-[11.5px] ${
                  active
                    ? "border-primary/60 bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:border-primary/40"
                }`}
              >
                {label}
                <span className="ml-1 opacity-60">{group.total}</span>
              </button>
            );
          })}
        </div>
      )}

      {tagSummary.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5">
          {tagSummary.map((tag) => {
            const active = selectedTags.includes(tag.tag);
            return (
              <button
                key={tag.tag}
                type="button"
                onClick={() => toggleTag(tag.tag)}
                className={`rounded-full border px-2.5 py-0.5 text-[11.5px] ${
                  active
                    ? "border-primary/60 bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:border-primary/40"
                }`}
              >
                {tag.tag}
                <span className="ml-1 opacity-60">{tag.count}</span>
              </button>
            );
          })}
        </div>
      )}

      {selectedIds.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-accent/40 px-3 py-2">
          <span className="text-[12px] text-muted-foreground">
            {t("space.bank.selected", { count: selectedIds.length })}
          </span>
          {batchMode === null ? (
            <>
              <button
                type="button"
                onClick={() => setBatchMode("add")}
                className="h-7 rounded-full border border-border px-2.5 text-[12px]"
              >
                {t("space.bank.batchAdd")}
              </button>
              <button
                type="button"
                onClick={() => setBatchMode("remove")}
                className="h-7 rounded-full border border-border px-2.5 text-[12px]"
              >
                {t("space.bank.batchRemove")}
              </button>
              <button
                type="button"
                onClick={() => setSelectedIds([])}
                className="h-7 rounded-full border border-border px-2.5 text-[12px]"
              >
                {t("space.bank.clearSelection")}
              </button>
            </>
          ) : (
            <>
              <input
                autoFocus
                value={batchDraft}
                onChange={(event) => setBatchDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") void applyBatch();
                  if (event.key === "Escape") setBatchMode(null);
                }}
                placeholder={t("space.bank.tagPlaceholder")}
                className="h-7 w-36 rounded-lg border border-border bg-background px-2 text-[12px] outline-none focus:border-primary/40"
              />
              <button
                type="button"
                onClick={() => void applyBatch()}
                className="h-7 rounded-full bg-primary px-2.5 text-[12px] font-medium text-primary-foreground"
              >
                {t("tag.apply")}
              </button>
              <button
                type="button"
                onClick={() => setBatchMode(null)}
                className="h-7 rounded-full border border-border px-2.5 text-[12px]"
              >
                {t("tag.cancel")}
              </button>
            </>
          )}
        </div>
      )}

      {error && <p className="text-[12px] text-destructive">{error}</p>}

      {visibleEntries.length === 0 ? (
        <p className="text-[13px] text-muted-foreground">
          {loading ? t("space.bank.loading") : t("space.bank.empty")}
        </p>
      ) : (
        <ul className="space-y-2">
          {visibleEntries.map((entry) => (
            <li key={entry.entry_id} className="rounded-xl border border-border p-3.5">
              <div className="flex items-start gap-2.5">
                <input
                  type="checkbox"
                  checked={selectedIds.includes(entry.entry_id)}
                  onChange={() => toggleSelected(entry.entry_id)}
                  className="mt-1"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                    <span className="rounded bg-muted px-1.5 py-0.5">{entry.question_type}</span>
                    <span>{entry.path_name || entry.path_id}</span>
                    {entry.knowledge_point_name && <span>· {entry.knowledge_point_name}</span>}
                    {(entry.knowledge_bases ?? []).map((kb) => (
                      <span
                        key={kb.id}
                        className="rounded bg-primary/10 px-1.5 py-0.5 text-primary"
                      >
                        {kb.name}
                      </span>
                    ))}
                    <span
                      className={`inline-flex items-center gap-1 ${
                        entry.result === "correct" ? "text-primary" : "text-destructive"
                      }`}
                    >
                      {entry.result === "correct" ? (
                        <CheckCircle2 size={11} />
                      ) : (
                        <XCircle size={11} />
                      )}
                      {entry.result}
                    </span>
                  </div>

                  <MarkdownMessage content={entry.question} className="mt-2 text-[13px]" />

                  <p className="mt-1.5 text-[12px] text-muted-foreground">
                    {t("space.bank.answer")}: {entry.correct_answer || "—"}
                  </p>
                  {entry.user_answer && (
                    <p className="mt-0.5 text-[12px] text-muted-foreground">
                      {t("space.bank.userAnswer")}: {entry.user_answer}
                    </p>
                  )}

                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    {entry.tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-[11px]"
                      >
                        {tag}
                        <button
                          type="button"
                          aria-label={t("space.bank.removeTag")}
                          onClick={() =>
                            void saveTags(
                              entry,
                              entry.tags.filter((item) => item !== tag),
                            )
                          }
                          className="text-muted-foreground hover:text-destructive"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                    {editingId === entry.entry_id ? (
                      <input
                        autoFocus
                        value={tagDraft}
                        onChange={(event) => setTagDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") void commitDraftTag(entry);
                          if (event.key === "Escape") {
                            setEditingId("");
                            setTagDraft("");
                          }
                        }}
                        placeholder={t("space.bank.tagPlaceholder")}
                        className="h-6 w-28 rounded-full border border-border bg-background px-2 text-[11px] outline-none focus:border-primary/40"
                      />
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(entry.entry_id);
                          setTagDraft("");
                        }}
                        className="inline-flex items-center gap-0.5 rounded-full border border-dashed border-border px-2 py-0.5 text-[11px] text-muted-foreground hover:border-primary/40"
                      >
                        <Plus size={10} />
                        {t("space.bank.addTag")}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <TagManagerDialog
        open={tagDialogOpen}
        tags={tagSummary}
        onClose={() => setTagDialogOpen(false)}
        onChanged={() => void load()}
      />
    </section>
  );
}
