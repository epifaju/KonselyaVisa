import { useQuery } from "@tanstack/react-query";
import { User } from "oidc-client-ts";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type ApiResponse, type MeResponse } from "@/api/client";
import { usePublicOrg } from "@/api/usePublicOrg";
import { userManager } from "@/auth/userManager";
import { AgentHeader } from "@/components/AgentHeader";
import { Button } from "@/components/ui/button";
import { useOrganizationBrandTheme } from "@/hooks/useOrganizationBrandTheme";
import { useOrganizationBrandFavicon } from "@/hooks/useOrganizationBrandFavicon";
import { CatalogEditor } from "@/screens/CatalogEditor";
import { CatalogList } from "@/screens/CatalogList";
import { CaseDetail } from "@/screens/CaseDetail";
import { CaseQueue } from "@/screens/CaseQueue";
import { OrganizationSettings } from "@/screens/OrganizationSettings";
import { Slots } from "@/screens/Slots";

const STAFF_ROLES = ["AGENT", "SUPERVISOR", "BUSINESS_ADMIN", "PLATFORM_ADMIN"];

type View = "queue" | "slots" | "catalog" | "settings";

export default function App() {
  const { t, i18n } = useTranslation();
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState(false);
  const [view, setView] = useState<View>("queue");
  const [caseId, setCaseId] = useState<string | null>(null);
  const [procedureId, setProcedureId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [overdueOnly, setOverdueOnly] = useState(false);

  useEffect(() => {
    const isCallback = window.location.pathname === "/callback";
    const bootstrap = async () => {
      try {
        if (isCallback) {
          try {
            const signedIn = await userManager.signinRedirectCallback();
            setUser(signedIn);
            setError(false);
          } catch {
            const existing = await userManager.getUser();
            setUser(existing);
            // Callback can fail after a successful session (reload / double hit) — only surface if still anonymous.
            setError(!existing || existing.expired);
          }
          window.history.replaceState({}, document.title, "/");
        } else {
          setUser(await userManager.getUser());
        }
      } catch {
        setError(true);
        if (isCallback) {
          window.history.replaceState({}, document.title, "/");
        }
      } finally {
        setReady(true);
      }
    };
    void bootstrap();
    const onLoaded = (loaded: User) => setUser(loaded);
    userManager.events.addUserLoaded(onLoaded);
    return () => userManager.events.removeUserLoaded(onLoaded);
  }, []);

  const meQuery = useQuery({
    queryKey: ["me", user?.access_token],
    queryFn: async () => (await apiGet<ApiResponse<MeResponse>>(user!.access_token, "/api/v1/me")).data!,
    enabled: Boolean(user?.access_token),
  });
  const orgQuery = usePublicOrg();
  const branding = meQuery.data?.organization ?? orgQuery.data;
  useOrganizationBrandTheme(branding?.brandColor);
  useOrganizationBrandFavicon(branding?.faviconUrl);
  const organizationName = loc(branding?.nameI18n, i18n.language, t("login.organizationName"));
  const languages = branding?.activeLanguages?.length ? branding.activeLanguages : ["fr", "pt", "en"];

  if (!ready) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">{t("login.callback")}</p>
      </main>
    );
  }

  const roles = (meQuery.data?.roles ?? []).map((role) => role.replace(/^ROLE_/, ""));
  const isStaff = roles.some((role) => STAFF_ROLES.includes(role));
  const isSupervisor = roles.some((role) =>
    ["SUPERVISOR", "BUSINESS_ADMIN", "PLATFORM_ADMIN"].includes(role),
  );
  const isCatalogAdmin = roles.some((role) => ["BUSINESS_ADMIN", "PLATFORM_ADMIN"].includes(role));
  const isOrgAdmin = isCatalogAdmin;

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-8">
      <AgentHeader
        organizationName={organizationName}
        logoUrl={branding?.logoUrl}
        languages={languages}
        loggedIn={Boolean(user)}
        onLogout={() => void userManager.signoutRedirect()}
      />

      {error ? (
        <p className="mb-4 flex items-center gap-2 text-body-sm text-destructive" role="alert">
          {t("login.error")}
        </p>
      ) : null}

      {!user ? (
        <section className="mx-auto max-w-lg rounded-lg border bg-card p-8 shadow-sm">
          <p className="text-muted-foreground">{t("login.hint")}</p>
          <Button className="mt-6" type="button" onClick={() => void userManager.signinRedirect()}>
            {t("login.cta")}
          </Button>
        </section>
      ) : meQuery.isLoading ? (
        <p className="text-muted-foreground">{t("common.loading")}</p>
      ) : !isStaff ? (
        <p className="rounded-lg border border-border bg-card p-6 text-destructive">{t("login.forbidden")}</p>
      ) : (
        <>
          <nav className="mb-6 flex flex-wrap gap-2">
            <Button type="button" variant={view === "queue" ? "default" : "outline"} onClick={() => { setView("queue"); setCaseId(null); setProcedureId(null); }}>
              {t("nav.cases")}
            </Button>
            <Button type="button" variant={view === "slots" ? "default" : "outline"} onClick={() => { setView("slots"); setCaseId(null); setProcedureId(null); }}>
              {t("nav.slots")}
            </Button>
            {isCatalogAdmin ? (
              <Button type="button" variant={view === "catalog" ? "default" : "outline"} onClick={() => { setView("catalog"); setCaseId(null); }}>
                {t("nav.catalog")}
              </Button>
            ) : null}
            {isOrgAdmin ? (
              <Button type="button" variant={view === "settings" ? "default" : "outline"} onClick={() => { setView("settings"); setCaseId(null); setProcedureId(null); }}>
                {t("nav.settings")}
              </Button>
            ) : null}
          </nav>
          {view === "slots" ? (
            <Slots token={user.access_token} />
          ) : view === "settings" && isOrgAdmin ? (
            <OrganizationSettings token={user.access_token} />
          ) : view === "catalog" && isCatalogAdmin ? (
            procedureId ? (
              <CatalogEditor token={user.access_token} procedureId={procedureId} onBack={() => setProcedureId(null)} />
            ) : (
              <CatalogList token={user.access_token} onOpen={setProcedureId} />
            )
          ) : caseId ? (
            <CaseDetail
              token={user.access_token}
              caseId={caseId}
              onBack={() => setCaseId(null)}
              onOpenCase={setCaseId}
            />
          ) : (
            <CaseQueue
              token={user.access_token}
              status={statusFilter}
              supervisor={isSupervisor}
              overdueOnly={overdueOnly}
              onStatusChange={setStatusFilter}
              onOverdueOnlyChange={setOverdueOnly}
              onOpenCase={setCaseId}
            />
          )}
        </>
      )}
    </main>
  );
}
