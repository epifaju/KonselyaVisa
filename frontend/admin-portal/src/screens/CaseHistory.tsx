import { useQuery } from "@tanstack/react-query";
import { AlertCircle, CheckCircle2, Clock, User } from "lucide-react";
import { useTranslation } from "react-i18next";
import { apiGet, type ApiResponse, type CaseHistoryEvent } from "@/api/client";
import { cn } from "@/lib/utils";

type Props = { token: string; caseId: string };

function ActorIcon({ kind }: { kind: string }) {
  const className = "mt-0.5 h-4 w-4 shrink-0";
  if (kind === "AGENT") {
    return <User className={cn(className, "text-primary")} aria-hidden />;
  }
  if (kind === "SYSTEM") {
    return <CheckCircle2 className={cn(className, "text-muted-foreground")} aria-hidden />;
  }
  return <Clock className={cn(className, "text-foreground")} aria-hidden />;
}

export function CaseHistory({ token, caseId }: Props) {
  const { t, i18n } = useTranslation();
  const query = useQuery({
    queryKey: ["case-history", caseId],
    queryFn: async () =>
      (await apiGet<ApiResponse<CaseHistoryEvent[]>>(token, `/api/v1/cases/${caseId}/history`)).data ?? [],
  });

  return (
    <article className="rounded-lg border border-border bg-card p-4">
      <h3 className="font-medium text-foreground">{t("history.title")}</h3>
      {query.isLoading ? <p className="mt-2 text-body-sm text-muted-foreground">{t("common.loading")}</p> : null}
      {query.isError ? (
        <p className="mt-2 flex items-center gap-2 text-body-sm text-destructive">
          <AlertCircle className="h-4 w-4" aria-hidden />
          {t("common.error")}
        </p>
      ) : null}
      <ol className="mt-3 space-y-3">
        {(query.data ?? []).map((event) => (
          <li key={event.id} className="flex gap-2 text-body-sm">
            <ActorIcon kind={event.actorKind} />
            <div>
              <p className="text-foreground">
                {t(`history.event.${event.eventType}`, { defaultValue: event.eventType })}
                {event.requirementCode ? ` · ${event.requirementCode}` : ""}
              </p>
              <p className="text-caption text-muted-foreground">
                {t(`history.actor.${event.actorKind}`)} ·{" "}
                {new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "short" }).format(
                  new Date(event.occurredAt),
                )}
              </p>
            </div>
          </li>
        ))}
      </ol>
      {query.data?.length === 0 ? <p className="mt-2 text-body-sm text-muted-foreground">{t("history.empty")}</p> : null}
    </article>
  );
}
