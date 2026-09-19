import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type CatalogProcedure, type PageResponse } from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type Props = { token: string; onOpen: (id: string) => void };

function statusVariant(status: string | undefined) {
  if (status === "PUBLISHED") return "success" as const;
  if (status === "DRAFT") return "warning" as const;
  return "secondary" as const;
}

export function CatalogList({ token, onOpen }: Props) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const query = useQuery({
    queryKey: ["catalog-procedures"],
    queryFn: async () =>
      (await apiGet<PageResponse<CatalogProcedure>>(token, "/api/v1/procedures?size=50")).content ?? [],
  });

  if (query.isLoading) {
    return <p className="text-muted-foreground">{t("common.loading")}</p>;
  }
  if (query.isError) {
    return (
      <p className="text-destructive" role="alert">
        {t("common.error")}
      </p>
    );
  }

  const rows = query.data ?? [];

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-xl font-semibold">{t("catalog.title")}</h2>
        <p className="text-body-sm text-muted-foreground">{t("catalog.subtitle")}</p>
      </div>
      {rows.length === 0 ? (
        <p className="rounded-lg border bg-card p-6 text-muted-foreground">{t("catalog.empty")}</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border bg-card">
          <table className="w-full text-left text-body-sm">
            <thead className="border-b bg-muted/40 text-caption text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t("catalog.code")}</th>
                <th className="px-3 py-2 font-medium">{t("catalog.name")}</th>
                <th className="px-3 py-2 font-medium">{t("catalog.corridor")}</th>
                <th className="px-3 py-2 font-medium">{t("catalog.category")}</th>
                <th className="px-3 py-2 font-medium">{t("catalog.version")}</th>
                <th className="px-3 py-2 font-medium">{t("catalog.status")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((procedure) => {
                const latest = procedure.versions[0];
                const published = procedure.versions.find((version) => version.status === "PUBLISHED");
                const badgeStatus = latest?.status ?? (procedure.active ? "PUBLISHED" : "ARCHIVED");
                return (
                  <tr key={procedure.id} className="border-b last:border-0">
                    <td className="px-3 py-2 font-medium">{procedure.code}</td>
                    <td className="px-3 py-2">{loc(procedure.nameI18n, lang)}</td>
                    <td className="px-3 py-2">
                      {loc(procedure.originCountry?.nameI18n, lang)} → {loc(procedure.destinationCountry?.nameI18n, lang)}
                    </td>
                    <td className="px-3 py-2">{procedure.category}</td>
                    <td className="px-3 py-2">
                      {published
                        ? `v${published.versionNumber}`
                        : procedure.publishedVersionNumber
                          ? `v${procedure.publishedVersionNumber}`
                          : "—"}
                    </td>
                    <td className="px-3 py-2">
                      <Badge variant={statusVariant(badgeStatus)}>{t(`catalog.versionStatus.${badgeStatus}`)}</Badge>
                    </td>
                    <td className="px-3 py-2 text-right">
                      <Button type="button" size="sm" variant="outline" onClick={() => onOpen(procedure.id)}>
                        {t("catalog.edit")}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
