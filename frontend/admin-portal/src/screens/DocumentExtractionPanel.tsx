import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

const FIELDS = ["given_name", "family_name", "document_number", "nationality"] as const;

type Props = {
  initial?: Record<string, string>;
  busy?: boolean;
  onCancel: () => void;
  onSubmit: (extractedFields: Record<string, string>) => void;
};

export function DocumentExtractionPanel({ initial, busy, onCancel, onSubmit }: Props) {
  const { t } = useTranslation();
  const [values, setValues] = useState<Record<string, string>>(() => {
    const next: Record<string, string> = {};
    for (const key of FIELDS) {
      next[key] = initial?.[key] ?? "";
    }
    return next;
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const extractedFields: Record<string, string> = {};
    for (const key of FIELDS) {
      const value = values[key]?.trim();
      if (value) {
        extractedFields[key] = value;
      }
    }
    if (Object.keys(extractedFields).length === 0) {
      return;
    }
    onSubmit(extractedFields);
  };

  return (
    <form onSubmit={submit} className="mt-3 space-y-3 rounded-md border border-border bg-muted/50 p-3">
      <p className="text-body-sm font-medium text-foreground">{t("case.extraction.title")}</p>
      <p className="text-caption text-muted-foreground">{t("case.extraction.hint")}</p>
      {FIELDS.map((key) => (
        <label key={key} className="block text-body-sm text-foreground">
          {t(`case.extraction.fields.${key}`)}
          <input
            className="mt-1 w-full rounded-sm border border-border bg-background px-2 py-1.5 text-body-sm"
            value={values[key]}
            onChange={(event) => setValues((current) => ({ ...current, [key]: event.target.value }))}
            autoComplete="off"
          />
        </label>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={busy}>
          {t("case.extraction.save")}
        </Button>
        <Button type="button" size="sm" variant="outline" onClick={onCancel}>
          {t("common.back")}
        </Button>
      </div>
    </form>
  );
}
