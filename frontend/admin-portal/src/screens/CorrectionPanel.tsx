import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { reasonsFor } from "@/data/correctionReasons";
import { Button } from "@/components/ui/button";

type Props = {
  requirementCode: string;
  busy?: boolean;
  onCancel: () => void;
  onSubmit: (reason: string) => void;
};

export function CorrectionPanel({ requirementCode, busy, onCancel, onSubmit }: Props) {
  const { t } = useTranslation();
  const reasons = reasonsFor(requirementCode);
  const [messageKey, setMessageKey] = useState(reasons[0]?.key ?? "document.correction.unreadable");
  const [note, setNote] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = note.trim();
    onSubmit(trimmed ? `${messageKey}\n${trimmed}` : messageKey);
  };

  return (
    <form onSubmit={submit} className="mt-3 space-y-3 rounded-md border border-border bg-muted/50 p-3">
      <label className="block text-body-sm font-medium text-foreground">
        {t("case.reasonLabel")}
        <select
          className="mt-1 w-full rounded-sm border border-border bg-background px-2 py-1.5 text-body-sm"
          value={messageKey}
          onChange={(event) => setMessageKey(event.target.value)}
        >
          {reasons.map((reason) => (
            <option key={reason.key} value={reason.key}>
              {t(reason.key)}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-body-sm text-foreground">
        {t("case.reasonNote")}
        <textarea
          className="mt-1 w-full rounded-sm border border-border bg-background p-2 text-body-sm"
          rows={2}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder={t("case.reasonNotePlaceholder")}
        />
      </label>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" variant="destructive" disabled={busy}>
          {t("case.sendCorrection")}
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel}>
          {t("common.back")}
        </Button>
      </div>
    </form>
  );
}
