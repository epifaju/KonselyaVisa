import { useTranslation } from "react-i18next";
import { loc } from "@/api/client";
import { usePublicOrg } from "@/api/usePublicOrg";
import { userManager } from "@/auth/userManager";
import { OrganizationBrand } from "@/components/OrganizationBrand";
import { Button } from "@/components/ui/button";

const agentPortalUrl = import.meta.env.VITE_AGENT_PORTAL_URL ?? "http://localhost:5176";

export function StaffPortalGate() {
  const { t, i18n } = useTranslation();
  const orgQuery = usePublicOrg();
  const orgName = loc(orgQuery.data?.nameI18n, i18n.language, t("login.organizationName"));

  return (
    <div className="login-theme min-h-screen bg-background text-foreground">
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center px-6 py-12">
        <OrganizationBrand layout="login" organizationName={orgName} />
        <p className="mt-8 text-center text-body text-foreground">{t("login.staffGate.title")}</p>
        <p className="mt-2 text-center text-body-sm text-muted-foreground">{t("login.staffGate.hint")}</p>
        <Button className="mt-8 h-12 w-full rounded-md text-body" type="button" asChild>
          <a href={agentPortalUrl}>{t("login.staffGate.cta")}</a>
        </Button>
        <Button
          className="mt-3 w-full"
          type="button"
          variant="outline"
          onClick={() => void userManager.signoutRedirect()}
        >
          {t("login.logout")}
        </Button>
      </main>
    </div>
  );
}
