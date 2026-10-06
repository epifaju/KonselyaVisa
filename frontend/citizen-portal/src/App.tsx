import { useQuery } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { User } from "oidc-client-ts";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type ApiResponse, type Me } from "@/api/client";
import { usePublicOrg } from "@/api/usePublicOrg";
import { userManager } from "@/auth/userManager";
import { CitizenHeader } from "@/components/CitizenHeader";
import { useOrganizationBrandTheme } from "@/hooks/useOrganizationBrandTheme";
import { useOrganizationBrandFavicon } from "@/hooks/useOrganizationBrandFavicon";
import { EligibilityAssistant } from "@/screens/EligibilityAssistant";
import { GuestAccountRegister } from "@/screens/GuestAccountRegister";
import { GuestTicketAttach } from "@/screens/GuestTicketAttach";
import { LoginScreen } from "@/screens/LoginScreen";
import { PublicHome } from "@/screens/public/PublicHome";
import { PublicLegalPage } from "@/screens/public/PublicLegalPage";
import { PublicTrackCase } from "@/screens/public/PublicTrackCase";
import { isConsularStaff, rolesFromSession } from "@/identity/isConsularStaff";
import { CaseJourney } from "@/screens/CaseJourney";
import { CaseList } from "@/screens/CaseList";
import { CompanyCaseList, isCompanyUser } from "@/screens/CompanyCaseList";
import { NewCase } from "@/screens/NewCase";
import { PrivacyAccount } from "@/screens/PrivacyAccount";
import { StaffPortalGate } from "@/screens/StaffPortalGate";

type View = "list" | "new" | "detail" | "privacy";
type PublicView = "home" | "login" | "wizard" | "register" | "track" | "legal" | "privacy" | "contact";

export default function App() {
  const { t, i18n } = useTranslation();
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState(false);
  const [guest, setGuest] = useState(false);
  const [publicView, setPublicView] = useState<PublicView>("home");
  const [wizardCategory, setWizardCategory] = useState<string | undefined>();
  const [view, setView] = useState<View>("list");
  const [caseId, setCaseId] = useState<string | null>(null);

  useEffect(() => {
    const isCallback = window.location.pathname === "/callback";
    const bootstrap = async () => {
      try {
        if (isCallback) {
          try {
            const signedIn = await userManager.signinRedirectCallback();
            setUser(signedIn);
          } catch {
            setUser(await userManager.getUser());
          }
          window.history.replaceState({}, document.title, "/");
        } else {
          const loaded = await userManager.getUser();
          if (loaded?.expired) {
            await userManager.removeUser();
            setUser(null);
          } else {
            setUser(loaded);
          }
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
    const onUnloaded = () => setUser(null);
    userManager.events.addUserLoaded(onLoaded);
    userManager.events.addUserUnloaded(onUnloaded);
    userManager.events.addSilentRenewError(() => {
      void userManager.removeUser();
      setUser(null);
    });
    return () => {
      userManager.events.removeUserLoaded(onLoaded);
      userManager.events.removeUserUnloaded(onUnloaded);
    };
  }, []);

  useEffect(() => {
    if (user?.expired) {
      void userManager.signinSilent().catch(() => {
        void userManager.removeUser();
        setUser(null);
      });
    }
  }, [user]);

  const token = user && !user.expired ? user.access_token : "";

  const meQuery = useQuery({
    queryKey: ["me", token],
    queryFn: async () => (await apiGet<ApiResponse<Me>>(token, "/api/v1/me")).data!,
    enabled: Boolean(token),
  });
  const orgQuery = usePublicOrg();
  useOrganizationBrandTheme(meQuery.data?.organization?.brandColor ?? orgQuery.data?.brandColor);
  useOrganizationBrandFavicon(meQuery.data?.organization?.faviconUrl ?? orgQuery.data?.faviconUrl);

  if (!ready) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">{t("login.callback")}</p>
      </main>
    );
  }

  if (!user) {
    if (publicView === "register") {
      return (
        <GuestAccountRegister
          onBack={() => {
            setGuest(false);
            setWizardCategory(undefined);
            setPublicView("home");
          }}
        />
      );
    }
    if (guest || publicView === "wizard") {
      return (
        <div className="login-theme min-h-screen bg-background">
          <main className="mx-auto max-w-3xl px-6 py-8">
            <EligibilityAssistant
              initialCategory={wizardCategory}
              onCancel={() => {
                setGuest(false);
                setWizardCategory(undefined);
                setPublicView("home");
              }}
              onCreated={() => {
                setGuest(false);
                setPublicView("home");
              }}
              onNeedSignIn={() => {
                setGuest(false);
                setPublicView("register");
              }}
            />
          </main>
        </div>
      );
    }
    if (publicView === "track") {
      return <PublicTrackCase onBack={() => setPublicView("home")} />;
    }
    if (publicView === "legal" || publicView === "privacy" || publicView === "contact") {
      return <PublicLegalPage kind={publicView} onBack={() => setPublicView("home")} />;
    }
    if (publicView === "login") {
      return (
        <LoginScreen
          bootstrapError={error}
          onBack={() => setPublicView("home")}
          onContinueGuest={() => {
            setGuest(true);
            setPublicView("wizard");
          }}
        />
      );
    }
    return (
      <PublicHome
        onStart={(category) => {
          setWizardCategory(category);
          setPublicView("wizard");
        }}
        onTrack={() => setPublicView("track")}
        onSignIn={() => setPublicView("login")}
        onNav={(next) => setPublicView(next === "home" ? "home" : next)}
      />
    );
  }

  const staffRoles = rolesFromSession(meQuery.data?.roles, user.profile);
  if (token && meQuery.isPending && !isConsularStaff(staffRoles)) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">{t("login.callback")}</p>
      </main>
    );
  }
  if (isConsularStaff(staffRoles)) {
    return <StaffPortalGate />;
  }

  const displayName =
    (typeof user.profile.name === "string" && user.profile.name) ||
    (typeof user.profile.preferred_username === "string" && user.profile.preferred_username) ||
    t("login.signedInFallback");

  return (
    <div className="login-theme min-h-screen bg-background">
    <main className={view === "list" && isCompanyUser(meQuery.data?.roles) ? "mx-auto max-w-6xl px-6 py-8" : "mx-auto max-w-3xl px-6 py-8"}>
      <CitizenHeader
        displayName={displayName}
        organizationName={loc(meQuery.data?.organization?.nameI18n, i18n.language, t("login.organizationName"))}
        logoUrl={meQuery.data?.organization?.logoUrl ?? orgQuery.data?.logoUrl}
        languages={meQuery.data?.organization?.activeLanguages ?? ["fr", "pt", "en"]}
        onCases={() => {
          setCaseId(null);
          setView("list");
        }}
        onPrivacy={() => setView("privacy")}
        onLogout={() => void userManager.signoutRedirect()}
      />
      {token ? (
        <GuestTicketAttach
          token={token}
          onCreated={(id) => {
            setCaseId(id);
            setView("detail");
          }}
        />
      ) : null}

      {error ? (
        <p className="mb-4 flex items-center gap-2 text-body-sm text-destructive" role="alert">
          <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
          <span>{t("login.error")}</span>
        </p>
      ) : null}

      {!token ? (
        <p className="text-muted-foreground">{t("login.callback")}</p>
      ) : view === "privacy" ? (
        <PrivacyAccount token={token} onBack={() => setView("list")} />
      ) : view === "new" ? (
        <NewCase
          token={token}
          defaultName={typeof user.profile.name === "string" ? user.profile.name : undefined}
          defaultEmail={typeof user.profile.email === "string" ? user.profile.email : undefined}
          onCancel={() => setView("list")}
          onCreated={(id) => {
            setCaseId(id);
            setView("detail");
          }}
        />
      ) : view === "detail" && caseId ? (
        <CaseJourney
          token={token}
          caseId={caseId}
          onBack={() => {
            setCaseId(null);
            setView("list");
          }}
        />
      ) : isCompanyUser(meQuery.data?.roles) ? (
        <CompanyCaseList
          token={token}
          onNew={() => setView("new")}
          onOpen={(id) => {
            setCaseId(id);
            setView("detail");
          }}
        />
      ) : (
        <CaseList
          token={token}
          onNew={() => setView("new")}
          onOpen={(id) => {
            setCaseId(id);
            setView("detail");
          }}
        />
      )}
    </main>
    </div>
  );
}
