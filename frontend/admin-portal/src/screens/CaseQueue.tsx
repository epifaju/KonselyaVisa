import { useQuery } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type ApiResponse, type CaseItem, type PageResponse, type SupervisorSummary } from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ageHours, ageTone } from "@/lib/caseAge";
import { shortCaseReference } from "@/lib/caseReference";
import { SupervisorAlert } from "@/screens/SupervisorAlert";
import { cn } from "@/lib/utils";

const TABS = ["", "CREATED", "IN_PROGRESS", "CORRECTION_REQUESTED", "COMPLETED", "CANCELLED"] as const;

type Props = {
  token: string;
  status: string;
  supervisor?: boolean;
  overdueOnly?: boolean;
  onStatusChange: (status: string) => void;
  onOverdueOnlyChange: (value: boolean) => void;
  onOpenCase: (id: string) => void;
};

function statusBadge(status: string): "success" | "warning" | "destructive" | "outline" {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "CORRECTION_REQUESTED") {
    return "destructive";
  }
  if (status === "IN_PROGRESS" || status === "CREATED") {
    return "warning";
  }
  return "outline";
}

function toneOf(item: CaseItem) {
  return ageTone(ageHours(item.createdAt), item.status, item.estimatedInstructionDays);
}

function formatWait(hours: number | null, t: (key: string, opts: { count: number }) => string): string {
  if (hours == null) {
    return "—";
  }
  if (hours < 24) {
    return t("queue.ageHours", { count: Math.max(1, Math.round(hours)) });
  }
  return t("queue.ageDays", { count: Math.max(1, Math.round(hours / 24)) });
}

export function CaseQueue({
  token,
  status,
  supervisor,
  overdueOnly,
  onStatusChange,
  onOverdueOnlyChange,
  onOpenCase,
}: Props) {
  const { t, i18n } = useTranslation();
  const summaryQuery = useQuery({
    queryKey: ["supervisor-summary"],
    enabled: Boolean(supervisor),
    queryFn: async () =>
      (await apiGet<ApiResponse<SupervisorSummary>>(token, "/api/v1/supervisor/summary")).data!,
  });
  const query = useQuery({
    queryKey: ["agent-cases", status],
    queryFn: () => {
      const params = new URLSearchParams({ size: "50", sort: "createdAt,asc" });
      if (status) {
        params.set("status", status);
      }
      return apiGet<PageResponse<CaseItem>>(token, `/api/v1/agent/cases?${params.toString()}`);
    },
  });

  const all = query.data?.content ?? [];
  const rows = overdueOnly ? all.filter((item) => toneOf(item) === "overdue") : all;
  const summary = summaryQuery.data;

  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-heading-2 font-medium text-foreground">{t("queue.title")}</h2>
        <p className="text-caption text-muted-foreground">{t("queue.ageHint")}</p>
      </div>

      {supervisor ? (
        <SupervisorAlert
          overdueCount={summary?.overdueCount ?? 0}
          warningCount={summary?.watchCount ?? 0}
          openCount={summary?.openCount ?? 0}
          onShowOverdue={() => {
            onStatusChange("");
            onOverdueOnlyChange(true);
          }}
        />
      ) : null}

      <div className="flex flex-wrap gap-1" role="tablist" aria-label={t("queue.filter")}>
        {TABS.map((value) => (
          <Button
            key={value || "all"}
            type="button"
            size="sm"
            variant={status === value && !overdueOnly ? "default" : "outline"}
            onClick={() => {
              onOverdueOnlyChange(false);
              onStatusChange(value);
            }}
          >
            {value ? t(`status.case.${value}`) : t("queue.all")}
          </Button>
        ))}
      </div>

      {query.isLoading ? <p className="text-caption text-muted-foreground">{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="flex items-center gap-2 text-caption text-destructive" role="alert">
          <AlertCircle className="h-4 w-4" aria-hidden />
          {t("common.error")}
        </p>
      ) : null}

      <div className="overflow-x-auto rounded-md border border-border bg-card">
        <table className="w-full text-left text-caption">
          <thead className="sticky top-0 bg-muted text-muted-foreground">
            <tr>
              <th className="px-2 py-1.5 font-medium">{t("queue.reference")}</th>
              <th className="px-2 py-1.5 font-medium">{t("queue.applicant")}</th>
              <th className="px-2 py-1.5 font-medium">{t("queue.procedure")}</th>
              <th className="px-2 py-1.5 font-medium">{t("queue.next")}</th>
              <th className="px-2 py-1.5 font-medium">{t("queue.status")}</th>
              <th className="px-2 py-1.5 font-medium">{t("queue.age")}</th>
              <th className="px-2 py-1.5" />
            </tr>
          </thead>
          <tbody>
            {rows.map((item) => {
              const hours = ageHours(item.createdAt);
              const tone = toneOf(item);
              const sla = item.estimatedInstructionDays;
              return (
                <tr
                  key={item.id}
                  tabIndex={0}
                  className={cn(
                    "cursor-pointer border-t border-border hover:bg-accent/40",
                    tone === "overdue" && "border-l-[3px] border-l-destructive bg-destructive/10",
                    tone === "warning" && "border-l-[3px] border-l-warning bg-warning/10",
                  )}
                  onClick={() => onOpenCase(item.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onOpenCase(item.id);
                    }
                  }}
                >
                  <td className="px-2 py-1 font-medium text-foreground">{shortCaseReference(item.reference)}</td>
                  <td className="max-w-[10rem] truncate px-2 py-1">
                    {item.applicant.displayName ?? item.applicant.email ?? "—"}
                  </td>
                  <td className="max-w-[12rem] truncate px-2 py-1">
                    {loc(item.procedureNameI18n, i18n.language, item.procedureCode)}
                  </td>
                  <td className="px-2 py-1 text-muted-foreground">
                    {item.nextAction
                      ? t(`queue.nextAction.${item.nextAction}`, {
                          defaultValue: t(`status.case.${item.status}`),
                        })
                      : "—"}
                  </td>
                  <td className="px-2 py-1">
                    <Badge variant={statusBadge(item.status)}>{t(`status.case.${item.status}`)}</Badge>
                  </td>
                  <td className="whitespace-nowrap px-2 py-1">
                    <span
                      className={cn(
                        tone === "overdue" && "font-medium text-destructive",
                        tone === "warning" && "font-medium text-warning",
                        tone === "ok" && "text-muted-foreground",
                      )}
                    >
                      {formatWait(hours, t)}
                    </span>
                    {sla != null ? (
                      <span className="text-muted-foreground">{` / ${t("queue.slaDays", { count: sla })}`}</span>
                    ) : null}
                  </td>
                  <td className="px-2 py-1 text-right">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={(event) => {
                        event.stopPropagation();
                        onOpenCase(item.id);
                      }}
                    >
                      {t("queue.open")}
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {query.data && rows.length === 0 ? (
          <p className="px-3 py-4 text-center text-caption text-muted-foreground">{t("queue.empty")}</p>
        ) : null}
      </div>
    </section>
  );
}
