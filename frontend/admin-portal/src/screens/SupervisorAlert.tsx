import { AlertCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

type Props = {
  overdueCount: number;
  warningCount: number;
  openCount: number;
  onShowOverdue: () => void;
};

export function SupervisorAlert({ overdueCount, warningCount, openCount, onShowOverdue }: Props) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      {overdueCount > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive bg-destructive/10 p-4" role="alert">
          <p className="flex items-center gap-2 text-body font-medium text-destructive">
            <AlertCircle className="h-5 w-5 shrink-0" aria-hidden />
            {t("supervisor.overdueAlert", { count: overdueCount })}
          </p>
          <Button type="button" variant="destructive" size="sm" onClick={onShowOverdue}>
            {t("supervisor.openOverdue")}
          </Button>
        </div>
      ) : null}
      <div className="grid gap-2 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-card p-3">
          <p className="text-caption text-muted-foreground">{t("supervisor.open")}</p>
          <p className="text-heading-2 font-medium text-foreground">{openCount}</p>
        </div>
        <div className="rounded-lg border border-border bg-card p-3">
          <p className="text-caption text-muted-foreground">{t("supervisor.watch")}</p>
          <p className="text-heading-2 font-medium text-foreground">{warningCount}</p>
        </div>
        <div className="rounded-lg border border-border bg-card p-3">
          <p className="text-caption text-muted-foreground">{t("supervisor.breach")}</p>
          <p className="text-heading-2 font-medium text-foreground">{overdueCount}</p>
        </div>
      </div>
    </div>
  );
}
