import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type ApiResponse } from "@/api/client";
import { Button } from "@/components/ui/button";
import { usePublicSite } from "@/screens/public/PublicHome";

type Props = {
  kind: "legal" | "privacy" | "contact";
  onBack: () => void;
};

export function PublicLegalPage({ kind, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const siteQuery = usePublicSite();
  const orgName = loc(siteQuery.data?.nameI18n, i18n.language, t("login.organizationName"));
  const notice = useQuery({
    queryKey: ["privacy-notice"],
    queryFn: async () =>
      (
        await apiGet<ApiResponse<{ version: string; textI18n: Record<string, string> }>>(
          "",
          "/api/v1/privacy-notices/current",
        )
      ).data!,
    enabled: kind === "privacy",
  });

  return (
    <div className="login-theme min-h-screen bg-background px-4 py-8 text-foreground">
      <main className="mx-auto max-w-2xl space-y-6">
        <Button type="button" variant="outline" onClick={onBack}>
          {t("common.back")}
        </Button>
        <h1 className="text-heading-2 font-medium">
          {kind === "legal" ? t("home.footer.legal") : kind === "privacy" ? t("home.footer.privacy") : t("home.nav.contact")}
        </h1>
        {kind === "legal" ? <p className="text-body text-muted-foreground">{t("home.legal.body", { organization: orgName })}</p> : null}
        {kind === "privacy" ? (
          <p className="text-body text-muted-foreground">
            {notice.data ? loc(notice.data.textI18n, i18n.language) : t("common.loading")}
          </p>
        ) : null}
        {kind === "contact" ? (
          <div className="space-y-2 text-body text-muted-foreground">
            <p className="font-medium text-foreground">{orgName}</p>
            <p>{loc(siteQuery.data?.addressI18n, i18n.language, "")}</p>
            <p>{loc(siteQuery.data?.openingHoursI18n, i18n.language, "")}</p>
            {siteQuery.data?.contactEmail ? <p>{siteQuery.data.contactEmail}</p> : null}
            {siteQuery.data?.contactPhone ? <p>{siteQuery.data.contactPhone}</p> : null}
          </div>
        ) : null}
      </main>
    </div>
  );
}
