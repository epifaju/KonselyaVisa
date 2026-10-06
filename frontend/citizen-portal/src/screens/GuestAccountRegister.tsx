import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError, apiGet, apiPost, loc, type ApiResponse } from "@/api/client";
import { usePublicOrg } from "@/api/usePublicOrg";
import { userManager } from "@/auth/userManager";
import { OrganizationBrand } from "@/components/OrganizationBrand";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { readGuestTicket } from "@/lib/guestTicket";

type Props = {
  onBack: () => void;
};

export function GuestAccountRegister({ onBack }: Props) {
  const { t, i18n } = useTranslation();
  const orgQuery = usePublicOrg();
  const [draft] = useState(() => readGuestTicket());
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [consent, setConsent] = useState(false);
  const [mismatch, setMismatch] = useState(false);
  const redirected = useRef(false);

  const notice = useQuery({
    queryKey: ["privacy-notice"],
    queryFn: async () =>
      (
        await apiGet<ApiResponse<{ version: string; textI18n: Record<string, string> }>>(
          "",
          "/api/v1/privacy-notices/current",
        )
      ).data!,
  });

  useEffect(() => {
    if (!draft && !redirected.current) {
      redirected.current = true;
      onBack();
    }
  }, [draft, onBack]);

  const register = useMutation({
    mutationFn: () =>
      apiPost<ApiResponse<{ created: boolean }>>("", "/api/v1/public/account-registrations", {
        eligibilityTicketId: draft?.ticketId,
        email,
        password,
        privacyConsent: {
          accepted: true,
          locale: i18n.language.slice(0, 2),
          noticeVersion: notice.data?.version ?? draft?.noticeVersion,
        },
      }),
    onSuccess: async () => {
      await userManager.signinRedirect({
        extraQueryParams: { ui_locales: i18n.language, login_hint: email.trim() },
      });
    },
  });

  const errorKey = register.error instanceof ApiError ? register.error.messageKey : undefined;
  const orgName = loc(orgQuery.data?.nameI18n, i18n.language, t("login.organizationName"));
  const canSubmit = Boolean(draft && email && password && passwordConfirm && consent && !register.isPending);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!draft) {
      onBack();
      return;
    }
    if (password !== passwordConfirm) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    register.mutate();
  };

  const onHaveAccount = () => {
    void userManager.signinRedirect({ extraQueryParams: { ui_locales: i18n.language } });
  };

  if (!draft) {
    return null;
  }

  return (
    <div className="login-theme min-h-screen bg-background text-foreground">
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center px-6 py-12">
        <OrganizationBrand layout="login" organizationName={orgName} />
        <form className="mt-10 w-full space-y-5" onSubmit={onSubmit}>
          <h2 className="text-center text-heading-3 font-medium text-foreground">{t("wizard.register.title")}</h2>
          <p className="text-center text-body-sm text-muted-foreground">{t("wizard.register.hint")}</p>
          <label className="block space-y-1">
            <span className="text-caption text-muted-foreground">{t("wizard.register.email")}</span>
            <Input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label className="block space-y-1">
            <span className="text-caption text-muted-foreground">{t("wizard.register.password")}</span>
            <Input
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <span className="text-caption text-muted-foreground">{t("wizard.register.passwordHint")}</span>
          </label>
          <label className="block space-y-1">
            <span className="text-caption text-muted-foreground">{t("wizard.register.passwordConfirm")}</span>
            <Input
              type="password"
              autoComplete="new-password"
              required
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
            />
          </label>
          <label className="flex items-start gap-2 text-body-sm">
            <input
              className="mt-1"
              type="checkbox"
              checked={consent}
              onChange={(event) => setConsent(event.target.checked)}
            />
            <span>{notice.data ? loc(notice.data.textI18n, i18n.language) : t("privacy.checkbox")}</span>
          </label>
          {mismatch ? (
            <p className="flex items-start gap-2 text-body-sm text-destructive" role="alert">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{t("wizard.register.passwordMismatch")}</span>
            </p>
          ) : null}
          {errorKey ? (
            <p className="flex items-start gap-2 text-body-sm text-destructive" role="alert">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{t(errorKey, { defaultValue: t("common.error") })}</span>
            </p>
          ) : null}
          <Button className="h-12 w-full rounded-md text-body-lg" type="submit" disabled={!canSubmit}>
            {register.isPending ? t("common.loading") : t("wizard.register.submit")}
          </Button>
          <Button className="w-full" type="button" variant="ghost" onClick={onHaveAccount}>
            {t("wizard.register.haveAccount")}
          </Button>
          <Button className="w-full" type="button" variant="ghost" onClick={onBack}>
            {t("common.back")}
          </Button>
        </form>
      </main>
    </div>
  );
}
