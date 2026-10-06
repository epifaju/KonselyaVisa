import { Ban } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { DocumentUploadRefusal } from "@/api/client";

type Props = {
  refusals: DocumentUploadRefusal[];
};

export function DocumentUploadRefusalPanel({ refusals }: Props) {
  const { t, i18n } = useTranslation();
  if (refusals.length === 0) {
    return null;
  }

  const formatWhen = (iso: string) => {
    try {
      return new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  const reasonLabel = (reasonKey: string) => {
    const short = reasonKey.replace(/^error\.(document|case)\./, "");
    return t(`case.uploadRefusal.reasons.${short}`, { defaultValue: reasonKey });
  };

  return (
    <aside
      className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-3 text-body-sm text-foreground"
      role="status"
    >
      <p className="flex items-start gap-2 font-medium text-destructive">
        <Ban className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <span>{t("case.uploadRefusal.title", { count: refusals.length })}</span>
      </p>
      <p className="mt-1 text-caption text-muted-foreground">{t("case.uploadRefusal.hint")}</p>
      <ul className="mt-3 space-y-2">
        {refusals.map((refusal) => (
          <li key={refusal.id} className="flex flex-col gap-0.5 border-t border-border/60 pt-2 first:border-t-0 first:pt-0">
            <span className="font-medium">{reasonLabel(refusal.reasonKey)}</span>
            <span className="text-caption text-muted-foreground">
              {t("case.uploadRefusal.detail", {
                requirement: refusal.requirementCode ?? "—",
                filename: refusal.originalFilename ?? "—",
                when: formatWhen(refusal.attemptedAt),
              })}
            </span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
