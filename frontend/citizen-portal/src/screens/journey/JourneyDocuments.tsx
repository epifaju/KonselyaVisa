import { AlertCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { downloadDocument, loc, type CaseDocument, type DocumentRequirement } from "@/api/client";
import { cn } from "@/lib/utils";

type Props = {
  caseId: string;
  token: string;
  requirements: DocumentRequirement[];
  documents: CaseDocument[];
  language: string;
  busy?: boolean;
  allowUpload?: boolean;
  onUpload: (code: string, file: File) => void;
};

export function JourneyDocuments({
  caseId,
  token,
  requirements,
  documents,
  language,
  busy,
  allowUpload = true,
  onUpload,
}: Props) {
  const { t, i18n } = useTranslation();
  const latestByCode = (code: string) =>
    [...documents].reverse().find((document) => document.requirementCode === code);

  return (
    <ul className="space-y-3">
      {requirements.map((requirement) => {
        const existing = latestByCode(requirement.code);
        const needsFix = existing?.status === "CORRECTION_REQUESTED" || existing?.status === "REJECTED";
        const unverified = existing?.status === "UPLOADED" || existing?.status === "REPLACED";
        return (
          <li key={requirement.code} className="rounded-md border border-border p-3">
            <p className="font-medium text-foreground">{loc(requirement.labelI18n, language, requirement.code)}</p>
            {existing ? (
              <p className={cn("mt-1 text-body-sm", needsFix ? "text-destructive" : "text-muted-foreground")}>
                {needsFix
                  ? t(existing.reviewMessageKey ?? "status.document.CORRECTION_REQUESTED")
                  : unverified
                    ? t("journey.depositedOn", { date: formatDay(existing.createdAt, i18n.language) })
                    : t(`status.document.${existing.status}`, { defaultValue: existing.status })}
              </p>
            ) : (
              <p className="mt-1 text-body-sm text-muted-foreground">{t("journey.missing")}</p>
            )}
            {existing ? (
              <button
                type="button"
                className="mt-2 text-body-sm text-primary underline"
                onClick={() => void downloadDocument(token, caseId, existing.id, existing.originalFilename)}
              >
                {t("journey.viewFile")}
              </button>
            ) : null}
            {needsFix ? (
              <p className="mt-2 flex items-center gap-2 text-body-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
                {t("journey.replaceFile")}
              </p>
            ) : null}
            {allowUpload && (needsFix || !existing) ? (
              <label className="mt-3 block text-body-sm text-foreground">
                {needsFix ? t("journey.replaceFile") : t("journey.chooseFile")}
                <input
                  className="mt-1 block w-full text-body-sm"
                  type="file"
                  accept="application/pdf,image/jpeg,image/png,image/webp"
                  disabled={busy}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) {
                      onUpload(requirement.code, file);
                      event.target.value = "";
                    }
                  }}
                />
              </label>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

export function depositedCount(documents: CaseDocument[], requirements: DocumentRequirement[]): number {
  return requirements.filter((requirement) =>
    documents.some(
      (document) =>
        document.requirementCode === requirement.code &&
        document.status !== "REJECTED",
    ),
  ).length;
}

function formatDay(value: string | undefined, language: string): string {
  if (!value) {
    return "—";
  }
  return new Intl.DateTimeFormat(language, { dateStyle: "medium" }).format(new Date(value));
}
