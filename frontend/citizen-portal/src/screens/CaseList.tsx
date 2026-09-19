import { useQuery } from "@tanstack/react-query";
import { AlertCircle, ChevronDown, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError, apiGet, type CaseItem, type PageResponse } from "@/api/client";
import { Button } from "@/components/ui/button";
import { CitizenCaseCard } from "@/screens/cases/CitizenCaseCard";
import { cn } from "@/lib/utils";

type Props = {
  token: string;
  onOpen: (id: string) => void;
  onNew: () => void;
};

const SECTION_ORDER = ["ACTION_REQUIRED", "IN_INSTRUCTION", "CLOSED"] as const;

export function CaseList({ token, onOpen, onNew }: Props) {
  const { t } = useTranslation();

  const query = useQuery({
    queryKey: ["my-cases", token],
    queryFn: () => apiGet<PageResponse<CaseItem>>(token, "/api/v1/cases?size=50"),
    retry: 2,
  });
  const errorKey = query.error instanceof ApiError ? query.error.messageKey : undefined;
  const all = query.data?.content ?? [];
  const openCount = all.filter((item) => item.listUrgencyGroup !== "CLOSED").length;

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-heading-1 font-medium text-foreground">{t("cases.title")}</h1>
          {query.isSuccess ? (
            <p className="mt-1 text-body-sm text-muted-foreground">
              {t("cases.inProgressCount", { count: openCount })}
            </p>
          ) : null}
        </div>
        <Button type="button" className="gap-2 self-start" onClick={onNew}>
          <Plus className="h-4 w-4" aria-hidden />
          {t("cases.new")}
        </Button>
      </div>

      {query.isLoading ? (
        <p className="text-body-sm text-muted-foreground" role="status">
          {t("common.loading")}
        </p>
      ) : null}

      {query.isError ? (
        <div className="flex flex-col gap-2" role="alert">
          <p className="flex items-center gap-2 text-body-sm text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
            <span>{t(errorKey ?? "common.error", { defaultValue: t("common.error") })}</span>
          </p>
          <Button type="button" variant="outline" onClick={() => void query.refetch()}>
            {t("common.retry")}
          </Button>
        </div>
      ) : null}

      {query.isSuccess && all.length === 0 ? (
        <p className="rounded-lg border border-border bg-card p-6 text-center text-body-sm text-muted-foreground">
          {t("cases.empty")} {t("cases.emptyHint")}
        </p>
      ) : null}

      {SECTION_ORDER.map((group) => {
        const items = all.filter((item) => item.listUrgencyGroup === group);
        if (items.length === 0) {
          return null;
        }
        if (group === "CLOSED") {
          return <ClosedSection key={group} items={items} onOpen={onOpen} />;
        }
        return (
          <section key={group} className="space-y-2">
            <h2 className={cn("text-caption font-medium uppercase tracking-wide", sectionTone(group))}>
              {t(`cases.section.${group}`)}
            </h2>
            <ul className="space-y-2">
              {items.map((item) => (
                <li key={item.id}>
                  <CitizenCaseCard item={item} onOpen={onOpen} />
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </section>
  );
}

function ClosedSection({ items, onOpen }: { items: CaseItem[]; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  return (
    <details className="group space-y-2 opacity-60">
      <summary className="flex cursor-pointer list-none items-center gap-2 text-caption font-medium uppercase tracking-wide text-muted-foreground">
        <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180" aria-hidden />
        {t("cases.section.CLOSED")}
      </summary>
      <ul className="space-y-2 pt-2">
        {items.map((item) => (
          <li key={item.id}>
            <CitizenCaseCard item={item} onOpen={onOpen} />
          </li>
        ))}
      </ul>
    </details>
  );
}

function sectionTone(group: (typeof SECTION_ORDER)[number]): string {
  if (group === "ACTION_REQUIRED") {
    return "text-destructive";
  }
  if (group === "IN_INSTRUCTION") {
    return "text-warning";
  }
  return "text-muted-foreground";
}
