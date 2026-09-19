import { useQuery } from "@tanstack/react-query";
import { User } from "oidc-client-ts";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, type ApiResponse, type MeResponse } from "@/api/client";
import { userManager } from "@/auth/userManager";
import { Button } from "@/components/ui/button";
import { CatalogEditor } from "@/screens/CatalogEditor";
import { CatalogList } from "@/screens/CatalogList";
import { CaseDetail } from "@/screens/CaseDetail";
import { CaseQueue } from "@/screens/CaseQueue";
import { Slots } from "@/screens/Slots";

const STAFF_ROLES = ["AGENT", "SUPERVISOR", "BUSINESS_ADMIN", "PLATFORM_ADMIN"];

type View = "queue" | "slots" | "catalog";

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
            setUser(await userManager.getUser());
            setError(true);
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

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-8">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-heading-1 font-medium text-foreground">{t("login.title")}</h1>
          <p className="text-body-sm text-muted-foreground">{t("login.subtitle")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {(["fr", "pt", "en"] as const).map((lng) => (
            <Button
              key={lng}
              type="button"
              size="sm"
              variant="ghost"
              aria-pressed={i18n.language.startsWith(lng)}
              onClick={() => void i18n.changeLanguage(lng)}
            >
              {t(`language.${lng}`)}
            </Button>
          ))}
          {user ? (
            <Button type="button" variant="outline" size="sm" onClick={() => void userManager.signoutRedirect()}>
              {t("login.logout")}
            </Button>
          ) : null}
        </div>
      </header>

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
          <nav className="mb-6 flex gap-2">
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
          </nav>
          {view === "slots" ? (
            <Slots token={user.access_token} />
          ) : view === "catalog" && isCatalogAdmin ? (
            procedureId ? (
              <CatalogEditor token={user.access_token} procedureId={procedureId} onBack={() => setProcedureId(null)} />
            ) : (
              <CatalogList token={user.access_token} onOpen={setProcedureId} />
            )
          ) : caseId ? (
            <CaseDetail token={user.access_token} caseId={caseId} onBack={() => setCaseId(null)} />
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
