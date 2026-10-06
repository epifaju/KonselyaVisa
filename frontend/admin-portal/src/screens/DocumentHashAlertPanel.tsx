import { AlertTriangle } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { DocumentHashAlert } from "@/api/client";
import { Button } from "@/components/ui/button";
import { shortCaseReference } from "@/lib/caseReference";

type Props = {
  alerts: DocumentHashAlert[];
  onOpenCase: (caseId: string) => void;
};

export function DocumentHashAlertPanel({ alerts, onOpenCase }: Props) {
  const { t } = useTranslation();
  const rows: { documentId: string; requirementCode: string; match: DocumentHashAlert["matches"][number] }[] = [];
  const seen = new Set<string>();
  for (const alert of alerts) {
    for (const match of alert.matches) {
      const key = `${alert.requirementCode}:${match.caseId}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      rows.push({ documentId: alert.documentId, requirementCode: alert.requirementCode, match });
    }
  }
  if (rows.length === 0) {
    return null;
  }

  return (
    <aside
      className="rounded-md border border-warning/40 bg-warning/10 px-3 py-3 text-body-sm text-warning"
      role="status"
    >
      <p className="flex items-start gap-2 font-medium">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <span>{t("case.hashAlert.title", { count: rows.length })}</span>
      </p>
      <p className="mt-1 text-caption text-warning/90">{t("case.hashAlert.hint")}</p>
      <ul className="mt-3 space-y-2">
        {rows.map((row) => (
            <li
              key={`${row.documentId}-${row.match.caseId}-${row.requirementCode}`}
              className="flex flex-wrap items-center justify-between gap-2"
            >
              <span>
                {t("case.hashAlert.match", {
                  requirement: row.requirementCode,
                  reference: shortCaseReference(row.match.caseReference),
                  applicant: row.match.applicantDisplayName,
                })}
              </span>
              <Button type="button" size="sm" variant="outline" onClick={() => onOpenCase(row.match.caseId)}>
                {t("case.hashAlert.open")}
              </Button>
            </li>
        ))}
      </ul>
    </aside>
  );
}
