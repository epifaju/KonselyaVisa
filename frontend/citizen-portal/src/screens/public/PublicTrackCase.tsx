import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiError, apiPost, loc, type ApiResponse } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PUBLIC_ORG } from "@/screens/public/PublicHome";

type Result = {
  reference: string;
  status: string;
  procedureNameI18n: Record<string, string>;
  nextActionMessageKey?: string;
};

type Props = {
  onBack: () => void;
};

export function PublicTrackCase({ onBack }: Props) {
  const { t, i18n } = useTranslation();
  const [reference, setReference] = useState("");
  const [email, setEmail] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");

  const lookup = useMutation({
    mutationFn: () =>
      apiPost<ApiResponse<Result>>("", "/api/v1/public/case-status", {
        organizationId: PUBLIC_ORG,
        reference: reference.trim(),
        email: email.trim() || undefined,
        dateOfBirth: dateOfBirth.trim() || undefined,
      }),
  });

  const errorKey = lookup.error instanceof ApiError ? lookup.error.messageKey : undefined;
  const result = lookup.data?.data;

  return (
    <div className="login-theme min-h-screen bg-background px-4 py-8 text-foreground">
      <main className="mx-auto max-w-lg space-y-6">
        <Button type="button" variant="outline" onClick={onBack}>
          {t("common.back")}
        </Button>
        <h1 className="text-heading-2 font-medium">{t("home.track.title")}</h1>
        <p className="text-body-sm text-muted-foreground">{t("home.track.hint")}</p>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            lookup.mutate();
          }}
        >
          <label className="block space-y-1 text-body-sm">
            <span>{t("home.track.reference")}</span>
            <Input value={reference} onChange={(event) => setReference(event.target.value)} required autoComplete="off" />
          </label>
          <label className="block space-y-1 text-body-sm">
            <span>{t("home.track.email")}</span>
            <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" />
          </label>
          <p className="text-center text-caption text-muted-foreground">{t("home.track.or")}</p>
          <label className="block space-y-1 text-body-sm">
            <span>{t("home.track.dob")}</span>
            <Input type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} />
          </label>
          <Button className="h-12 w-full" type="submit" disabled={lookup.isPending || !reference.trim() || (!email.trim() && !dateOfBirth)}>
            {lookup.isPending ? t("common.loading") : t("home.track.submit")}
          </Button>
        </form>
        {errorKey ? (
          <p className="text-body-sm text-destructive" role="alert">
            {t(errorKey, { defaultValue: t("home.track.notFound") })}
          </p>
        ) : null}
        {result ? (
          <div className="rounded-lg border border-border bg-card p-4">
            <p className="font-medium text-body">{result.reference}</p>
            <p className="mt-1 text-body-sm text-muted-foreground">
              {loc(result.procedureNameI18n, i18n.language)}
            </p>
            <p className="mt-2 text-body-sm">
              {t(`cases.nextStep.${result.status}`, {
                defaultValue: t(result.nextActionMessageKey ?? "", { defaultValue: result.status }),
              })}
            </p>
          </div>
        ) : null}
      </main>
    </div>
  );
}
