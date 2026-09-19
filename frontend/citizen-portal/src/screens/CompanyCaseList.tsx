import { useQuery } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiError, apiGet, loc, type ApiResponse, type CaseItem, type PageResponse } from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type Props = {
  token: string;
  onOpen: (id: string) => void;
  onNew: () => void;
};

type Summary = {
  total: number;
  byStatus: Record<string, number>;
};

const STATUS_ORDER = ["CREATED", "IN_PROGRESS", "CORRECTION_REQUESTED", "COMPLETED", "CANCELLED"] as const;

function statusVariant(status: string): "default" | "success" | "warning" | "destructive" | "secondary" {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "CORRECTION_REQUESTED" || status === "CANCELLED") {
    return "destructive";
  }
  if (status === "IN_PROGRESS") {
    return "warning";
  }
  return "secondary";
}

export function CompanyCaseList({ token, onOpen, onNew }: Props) {
  const { t, i18n } = useTranslation();
  const [statusFilter, setStatusFilter] = useState<string | "ALL">("ALL");

  const listQuery = useQuery({
    queryKey: ["company-cases", token],
    queryFn: () => apiGet<PageResponse<CaseItem>>(token, "/api/v1/cases?size=100&sort=createdAt,desc"),
    retry: 2,
  });
  const summaryQuery = useQuery({
    queryKey: ["company-cases-summary", token],
    queryFn: async () => (await apiGet<ApiResponse<Summary>>(token, "/api/v1/company/cases/summary")).data!,
    retry: 2,
  });

  const errorKey =
    listQuery.error instanceof ApiError
      ? listQuery.error.messageKey
      : summaryQuery.error instanceof ApiError
        ? summaryQuery.error.messageKey
        : undefined;

  const rows = useMemo(() => {
    const content = listQuery.data?.content ?? [];
    if (statusFilter === "ALL") {
      return content;
    }
    return content.filter((item) => item.status === statusFilter);
  }, [listQuery.data, statusFilter]);

  const byStatus = summaryQuery.data?.byStatus ?? {};

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-heading-1 font-medium text-foreground">{t("company.title")}</h1>
          <p className="mt-1 text-body-sm text-muted-foreground">{t("company.subtitle")}</p>
        </div>
        <Button type="button" onClick={onNew}>
          {t("company.new")}
        </Button>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          variant={statusFilter === "ALL" ? "default" : "outline"}
          onClick={() => setStatusFilter("ALL")}
        >
          {t("company.all")} ({summaryQuery.data?.total ?? 0})
        </Button>
        {STATUS_ORDER.map((status) => (
          <Button
            key={status}
            type="button"
            size="sm"
            variant={statusFilter === status ? "default" : "outline"}
            onClick={() => setStatusFilter(status)}
          >
            {t(`status.case.${status}`, { defaultValue: status })} ({byStatus[status] ?? 0})
          </Button>
        ))}
      </div>

      {listQuery.isLoading || summaryQuery.isLoading ? (
        <p className="text-body-sm text-muted-foreground" role="status">
          {t("common.loading")}
        </p>
      ) : null}

      {listQuery.isError || summaryQuery.isError ? (
        <div className="flex flex-col gap-2" role="alert">
          <p className="flex items-center gap-2 text-body-sm text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
            <span>{t(errorKey ?? "common.error", { defaultValue: t("common.error") })}</span>
          </p>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              void listQuery.refetch();
              void summaryQuery.refetch();
            }}
          >
            {t("common.retry")}
          </Button>
        </div>
      ) : null}

      {listQuery.isSuccess && rows.length === 0 ? (
        <p className="rounded-lg border border-border bg-card p-6 text-center text-body-sm text-muted-foreground">
          {t("company.empty")}
        </p>
      ) : null}

      {rows.length > 0 ? (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[40rem] text-left text-body-sm">
            <thead className="bg-muted text-caption font-medium text-muted-foreground">
              <tr>
                <th className="px-3 py-2">{t("company.procedure")}</th>
                <th className="px-3 py-2">{t("company.applicant")}</th>
                <th className="px-3 py-2">{t("company.status")}</th>
                <th className="px-3 py-2">{t("company.creator")}</th>
                <th className="px-3 py-2">{t("company.role")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <tr key={item.id} className="border-t border-border">
                  <td className="px-3 py-3">
                    <p className="font-medium text-foreground">
                      {loc(item.procedureNameI18n, i18n.language, item.procedureCode)}
                    </p>
                    <p className="text-caption text-muted-foreground">{item.reference}</p>
                  </td>
                  <td className="px-3 py-3 text-foreground">{item.applicant?.displayName ?? item.applicant?.email}</td>
                  <td className="px-3 py-3">
                    <Badge variant={statusVariant(item.status)}>{t(`status.case.${item.status}`)}</Badge>
                  </td>
                  <td className="px-3 py-3 text-foreground">{item.createdByLabel ?? item.createdBy}</td>
                  <td className="px-3 py-3">
                    {item.createdByRole
                      ? t(`company.roles.${item.createdByRole}`, { defaultValue: item.createdByRole })
                      : "—"}
                  </td>
                  <td className="px-3 py-3 text-right">
                    <Button type="button" variant="outline" size="sm" onClick={() => onOpen(item.id)}>
                      {t("company.open")}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

export function isCompanyUser(roles: string[] | undefined): boolean {
  if (!roles) {
    return false;
  }
  return roles.some((role) => role === "ROLE_COMPANY_USER" || role === "COMPANY_USER");
}
