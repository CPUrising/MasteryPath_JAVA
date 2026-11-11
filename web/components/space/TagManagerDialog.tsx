"use client";

import { useState } from "react";
import { useTranslation } from "react-i18next";

import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { Modal } from "@/components/common/Modal";
import { api } from "@/lib/api";
import type { QuestionTagSummary } from "@/lib/types";

/** 全局标签管理：重命名、合并、删除（工作区全量生效）。 */
export function TagManagerDialog({
  open,
  tags,
  onClose,
  onChanged,
}: {
  open: boolean;
  tags: QuestionTagSummary[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string[]>([]);
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState("");

  const toggle = (tag: string) =>
    setSelected((current) =>
      current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag],
    );

  const reset = () => {
    setSelected([]);
    setTarget("");
  };

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError("");
    try {
      await action();
      reset();
      onChanged();
    } catch (reason) {
      setError(String(reason));
    } finally {
      setBusy(false);
    }
  };

  const rename = () => {
    if (selected.length !== 1 || !target.trim()) return;
    void run(() => api.renameTag(selected[0], target.trim()));
  };

  const merge = () => {
    if (selected.length === 0 || !target.trim()) return;
    void run(() => api.mergeTags(selected, target.trim()));
  };

  const remove = (tag: string) => {
    setConfirming("");
    void run(() => api.deleteTag(tag));
  };

  return (
    <>
      <Modal open={open} title={t("tag.title")} onClose={onClose}>
        <p className="text-[12px] text-muted-foreground">{t("tag.selectHint")}</p>
        {tags.length === 0 ? (
          <p className="mt-3 text-[13px] text-muted-foreground">{t("tag.empty")}</p>
        ) : (
          <ul className="mt-3 max-h-64 space-y-1 overflow-y-auto">
            {tags.map((tag) => (
              <li key={tag.tag}>
                <label className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-accent/50">
                  <input
                    type="checkbox"
                    checked={selected.includes(tag.tag)}
                    onChange={() => toggle(tag.tag)}
                  />
                  <span className="min-w-0 flex-1 truncate text-[13px]">{tag.tag}</span>
                  <span className="text-[11px] text-muted-foreground">
                    {t("tag.count", { count: tag.count })}
                  </span>
                </label>
              </li>
            ))}
          </ul>
        )}

        <input
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          placeholder={t("tag.newName")}
          className="mt-3 h-9 w-full rounded-lg border border-border bg-background px-3 text-[13px] outline-none focus:border-primary/40"
        />

        {error && <p className="mt-2 text-[12px] text-destructive">{error}</p>}

        <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
          <button
            type="button"
            disabled={busy || selected.length !== 1 || !target.trim()}
            onClick={rename}
            className="h-9 rounded-lg border border-border px-3.5 text-[13px] disabled:opacity-40"
          >
            {t("tag.rename")}
          </button>
          <button
            type="button"
            disabled={busy || selected.length === 0 || !target.trim()}
            onClick={merge}
            className="h-9 rounded-lg border border-border px-3.5 text-[13px] disabled:opacity-40"
          >
            {t("tag.merge")}
          </button>
          <button
            type="button"
            disabled={busy || selected.length !== 1}
            onClick={() => setConfirming(selected[0])}
            className="h-9 rounded-lg border border-destructive/40 px-3.5 text-[13px] text-destructive disabled:opacity-40"
          >
            {t("tag.delete")}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="h-9 rounded-lg bg-primary px-3.5 text-[13px] font-medium text-primary-foreground"
          >
            {t("tag.apply")}
          </button>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirming !== ""}
        title={t("tag.delete")}
        description={t("tag.confirmDelete", { tag: confirming })}
        destructive
        busy={busy}
        onConfirm={() => remove(confirming)}
        onClose={() => setConfirming("")}
      />
    </>
  );
}
