import { useState } from "react";
import { useTranslation } from "react-i18next";
import { OrganizationBrand } from "@/components/OrganizationBrand";
import { userManager } from "@/auth/userManager";
import { Button } from "@/components/ui/button";

type Props = {
  bootstrapError?: boolean;
  onContinueGuest?: () => void;
  onBack?: () => void;
};

const agentPortalUrl = import.meta.env.VITE_AGENT_PORTAL_URL ?? "http://localhost:5176";

export function LoginScreen({ bootstrapError, onContinueGuest, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(Boolean(bootstrapError));

  const onSignIn = async () => {
    setSubmitting(true);
    setError(false);
    try {
      await userManager.signinRedirect({
        extraQueryParams: { ui_locales: i18n.language },
      });
    } catch {
      setError(true);
      setSubmitting(false);
    }
  };

  return (
    <div className="login-theme min-h-screen bg-background text-foreground">
      <div className="absolute right-4 top-4 flex gap-1">
        {(["fr", "pt", "en"] as const).map((lng) => (
          <Button
            key={lng}
            type="button"
            size="sm"
            variant={i18n.language.startsWith(lng) ? "outline" : "ghost"}
            className="h-8 px-2 text-caption"
            onClick={() => void i18n.changeLanguage(lng)}
          >
            {t(`language.${lng}`)}
          </Button>
        ))}
      </div>
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center px-6 py-12">
        <OrganizationBrand layout="login" />

        <div className="mt-10 w-full space-y-5">
          {onBack ? (
            <Button className="w-full" type="button" variant="ghost" onClick={onBack}>
              {t("common.back")}
            </Button>
          ) : null}
          {error ? <p className="text-center text-body-sm text-destructive">{t("login.error")}</p> : null}
          <Button className="h-12 w-full rounded-md text-body-lg" type="button" disabled={submitting} onClick={() => void onSignIn()}>
            {submitting ? t("common.loading") : t("login.cta")}
          </Button>
          <p className="text-center text-caption text-muted-foreground">{t("login.or")}</p>
          <Button
            className="h-12 w-full rounded-md text-body-lg"
            type="button"
            variant="outline"
            disabled={submitting}
            onClick={onContinueGuest}
          >
            {t("login.continueGuest")}
          </Button>
          <p className="text-center text-caption text-muted-foreground">{t("login.continueGuestHint")}</p>
        </div>

        <a className="mt-10 text-body-sm text-foreground underline" href={agentPortalUrl}>
          {t("login.agentLink")}
        </a>
      </main>
    </div>
  );
}
