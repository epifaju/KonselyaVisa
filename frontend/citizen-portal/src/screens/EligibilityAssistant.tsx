import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ApiError,
  apiGet,
  apiPost,
  loc,
  publishedVersion,
  type ApiResponse,
  type CaseItem,
  type PageResponse,
  type Procedure,
} from "@/api/client";
import { saveGuestTicket } from "@/lib/guestTicket";
import {
  CountryFlag,
  documentLeading,
  procedureLeading,
  RadioGroup,
  WizardOption,
  WizardShell,
} from "@/screens/wizard/WizardChrome";

const PUBLIC_ORG = import.meta.env.VITE_ORGANIZATION_ID ?? "11111111-1111-1111-1111-111111111111";

const NATIONALITIES = [
  { code: "FR", nameKey: "wizard.nationality.FR" },
  { code: "PT", nameKey: "wizard.nationality.PT" },
  { code: "GW", nameKey: "wizard.nationality.GW" },
] as const;

const DOCUMENT_TYPES = [
  { code: "BIRTH_CERTIFICATE", hintKey: "wizard.document.BIRTH_CERTIFICATE" },
  { code: "DIPLOMA", hintKey: "wizard.document.DIPLOMA" },
  { code: "CRIMINAL_RECORD", hintKey: "wizard.document.CRIMINAL_RECORD" },
] as const;

type Props = {
  token?: string;
  defaultName?: string;
  defaultEmail?: string;
  initialCategory?: string;
  onCreated: (id: string) => void;
  onCancel: () => void;
  onNeedSignIn?: () => void;
};

type ExtraFacts = {
  passportOk?: boolean;
  documentType?: string;
  hague?: boolean;
};

function uniqueDestinations(procedures: Procedure[]) {
  const seen = new Map<string, Procedure["destinationCountry"]>();
  for (const procedure of procedures) {
    seen.set(procedure.destinationCountry.isoCode, procedure.destinationCountry);
  }
  return [...seen.values()];
}

function clientEligible(procedure: Procedure, nationality: string, extra: ExtraFacts): boolean {
  if (procedure.category === "VISA") {
    return ["FR", "PT", "GW"].includes(nationality) && extra.passportOk === true;
  }
  if (procedure.category === "LEGALIZATION") {
    return (
      ["FR", "PT", "GW"].includes(nationality) &&
      DOCUMENT_TYPES.some((item) => item.code === extra.documentType)
    );
  }
  if (procedure.category === "APOSTILLE") {
    return ["FR", "PT"].includes(nationality) && extra.hague === true;
  }
  return true;
}

function applicantFacts(procedure: Procedure, nationality: string, extra: ExtraFacts): Record<string, unknown> {
  if (procedure.category === "VISA") {
    return { nationality, passportValidityMonths: extra.passportOk ? 12 : 3 };
  }
  if (procedure.category === "LEGALIZATION") {
    return { nationality, documentType: extra.documentType };
  }
  if (procedure.category === "APOSTILLE") {
    return { nationality, hagueConvention: extra.hague === true };
  }
  return { nationality };
}

export function EligibilityAssistant({
  token = "",
  defaultName,
  defaultEmail,
  initialCategory,
  onCreated,
  onCancel,
  onNeedSignIn,
}: Props) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [step, setStep] = useState(0);
  const [destinationCode, setDestinationCode] = useState("");
  const [procedureId, setProcedureId] = useState("");
  const [nationality, setNationality] = useState<(typeof NATIONALITIES)[number]["code"] | "">("");
  const [extra, setExtra] = useState<ExtraFacts>({});
  const [consent, setConsent] = useState(false);
  const [quitConfirm, setQuitConfirm] = useState(false);

  const guest = !token;
  const catalog = useQuery({
    queryKey: ["procedures", guest ? "public" : "auth"],
    queryFn: () =>
      apiGet<PageResponse<Procedure>>(
        token,
        guest ? "/api/v1/public/procedures?size=50" : "/api/v1/procedures?size=50",
      ),
  });
  const notice = useQuery({
    queryKey: ["privacy-notice"],
    queryFn: async () =>
      (
        await apiGet<ApiResponse<{ version: string; textI18n: Record<string, string> }>>(
          token,
          "/api/v1/privacy-notices/current",
        )
      ).data!,
  });

  const procedures = catalog.data?.content ?? [];
  const scoped = initialCategory ? procedures.filter((procedure) => procedure.category === initialCategory) : procedures;
  const seeded = useRef(false);
  useEffect(() => {
    if (seeded.current || !initialCategory || scoped.length === 0) {
      return;
    }
    seeded.current = true;
    const dests = uniqueDestinations(scoped);
    if (dests.length === 1) {
      setDestinationCode(dests[0]!.isoCode);
      const forDest = scoped.filter((procedure) => procedure.destinationCountry.isoCode === dests[0]!.isoCode);
      if (forDest.length === 1) {
        setProcedureId(forDest[0]!.id);
        setStep(2);
      } else {
        setStep(1);
      }
    }
  }, [initialCategory, scoped]);

  const destinations = uniqueDestinations(scoped);
  const forDestination = scoped.filter((procedure) => procedure.destinationCountry.isoCode === destinationCode);
  const selected = useMemo(
    () => scoped.find((procedure) => procedure.id === procedureId),
    [scoped, procedureId],
  );
  const version = publishedVersion(selected);
  const amount =
    version?.pricing?.amountMinor != null && version.pricing.currency
      ? `${(version.pricing.amountMinor / 100).toFixed(2)} ${version.pricing.currency}`
      : null;

  const extraReady =
    selected?.category === "VISA"
      ? extra.passportOk !== undefined
      : selected?.category === "LEGALIZATION"
        ? Boolean(extra.documentType)
        : selected?.category === "APOSTILLE"
          ? extra.hague !== undefined
          : true;
  const eligible = selected && nationality ? clientEligible(selected, nationality, extra) : false;
  const hasAnswers = Boolean(
    destinationCode || procedureId || nationality || extra.passportOk !== undefined || extra.documentType || extra.hague !== undefined,
  );

  const evaluateGuest = useMutation({
    mutationFn: () =>
      apiPost<ApiResponse<{ eligible: boolean; ticketId?: string }>>(token, "/api/v1/public/eligibility-tickets", {
        organizationId: PUBLIC_ORG,
        procedureDefinitionId: procedureId,
        facts: selected ? applicantFacts(selected, nationality, extra) : { nationality },
      }),
    onSuccess: (response) => {
      const ticketId = response.data?.ticketId;
      if (response.data?.eligible && ticketId) {
        saveGuestTicket({
          ticketId,
          procedureDefinitionId: procedureId,
          locale: i18n.language.slice(0, 2),
          noticeVersion: notice.data?.version,
        });
        onNeedSignIn?.();
        return;
      }
    },
  });

  const create = useMutation({
    mutationFn: () =>
      apiPost<ApiResponse<CaseItem>>(token, "/api/v1/cases", {
        procedureDefinitionId: procedureId,
        applicant: {
          email: defaultEmail || undefined,
          displayName: defaultName || undefined,
          facts: selected ? applicantFacts(selected, nationality, extra) : { nationality },
        },
        privacyConsent: {
          accepted: true,
          locale: i18n.language.slice(0, 2),
          noticeVersion: notice.data?.version,
        },
      }),
    onSuccess: (response) => {
      if (response.data?.id) {
        queryClient.setQueryData(["case", response.data.id], response.data);
        onCreated(response.data.id);
      }
    },
  });

  const errorKey =
    (create.error instanceof ApiError ? create.error.messageKey : undefined) ??
    (evaluateGuest.error instanceof ApiError ? evaluateGuest.error.messageKey : undefined) ??
    (evaluateGuest.isSuccess && evaluateGuest.data?.data?.eligible === false
      ? "error.eligibility.not_met"
      : undefined);
  const totalQuestions = 4;
  const onQuestions = step < totalQuestions;
  const questionStep = onQuestions ? step + 1 : totalQuestions;

  const goNext = () => setStep((value) => Math.min(value + 1, 4));
  const goBack = () => setStep((value) => Math.max(value - 1, 0));
  const requestQuit = () => {
    if (hasAnswers) {
      setQuitConfirm(true);
      return;
    }
    onCancel();
  };

  const canContinue =
    step === 0
      ? Boolean(destinationCode)
      : step === 1
        ? Boolean(procedureId)
        : step === 2
          ? Boolean(nationality)
          : step === 3
            ? extraReady
            : Boolean(eligible && consent);

  const question =
    step === 0
      ? t("wizard.destination.question")
      : step === 1
        ? t("wizard.procedure.question")
        : step === 2
          ? t("wizard.nationality.question")
          : step === 3
            ? selected?.category === "VISA"
              ? t("wizard.passport.question")
              : selected?.category === "LEGALIZATION"
                ? t("wizard.document.question")
                : t("wizard.hague.question")
            : t("wizard.confirm.title");

  const hint =
    step === 3 && selected?.category === "VISA"
      ? t("wizard.passport.hint")
      : step === 3 && selected?.category === "APOSTILLE"
        ? t("wizard.hague.hint")
        : undefined;

  const onContinue = () => {
    if (step === 4) {
      if (eligible && consent) {
        if (guest) {
          evaluateGuest.mutate();
        } else {
          create.mutate();
        }
      }
      return;
    }
    goNext();
  };

  return (
    <WizardShell
      progressLabel={t("wizard.progress", { current: questionStep, total: totalQuestions })}
      flowLabel={t("cases.new")}
      question={question}
      hint={hint}
      showProgress={onQuestions}
      progressRatio={questionStep / totalQuestions}
      quitLabel={t("wizard.quit")}
      onQuit={requestQuit}
      quitConfirm={quitConfirm}
      quitConfirmText={t("wizard.quitConfirm")}
      stayLabel={t("wizard.stay")}
      leaveLabel={t("wizard.leave")}
      onStay={() => setQuitConfirm(false)}
      onLeave={onCancel}
      backLabel={t("wizard.previous")}
      continueLabel={step === 4 ? (guest ? t("wizard.signInToCreate") : t("wizard.submit")) : t("wizard.continue")}
      backDisabled={step === 0}
      continueDisabled={!canContinue}
      continuePending={create.isPending || evaluateGuest.isPending}
      onBack={goBack}
      onContinue={onContinue}
    >
      {catalog.isLoading ? <p className="text-body-sm text-muted-foreground">{t("common.loading")}</p> : null}

      {step === 0 ? (
        <RadioGroup
          value={destinationCode}
          onValueChange={(value) => {
            setDestinationCode(value);
            setProcedureId("");
            setExtra({});
          }}
        >
          {destinations.map((country) => (
            <WizardOption
              key={country.isoCode}
              value={country.isoCode}
              selected={destinationCode === country.isoCode}
              label={loc(country.nameI18n, i18n.language, country.isoCode)}
              leading={<CountryFlag isoCode={country.isoCode} />}
            />
          ))}
        </RadioGroup>
      ) : null}

      {step === 1 ? (
        <RadioGroup
          value={procedureId}
          onValueChange={(value) => {
            setProcedureId(value);
            setExtra({});
          }}
        >
          {forDestination.map((procedure) => {
            const price = publishedVersion(procedure)?.pricing;
            const priceLabel =
              price?.amountMinor != null && price.currency
                ? `${(price.amountMinor / 100).toFixed(2)} ${price.currency}`
                : undefined;
            return (
              <WizardOption
                key={procedure.id}
                value={procedure.id}
                selected={procedureId === procedure.id}
                label={loc(procedure.nameI18n, i18n.language, procedure.code)}
                description={[loc(procedure.descriptionI18n, i18n.language, ""), priceLabel].filter(Boolean).join(" · ")}
                leading={procedureLeading(procedure.category)}
              />
            );
          })}
        </RadioGroup>
      ) : null}

      {step === 2 ? (
        <div className="space-y-3">
          <RadioGroup value={nationality} onValueChange={(value) => setNationality(value as typeof nationality)}>
            {NATIONALITIES.map((item) => (
              <WizardOption
                key={item.code}
                value={item.code}
                selected={nationality === item.code}
                label={t(item.nameKey)}
                leading={<CountryFlag isoCode={item.code} />}
              />
            ))}
          </RadioGroup>
          {selected?.category === "APOSTILLE" && nationality === "GW" ? (
            <p className="flex items-start gap-2 text-body-sm text-destructive" role="status">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{t("wizard.ineligible.nationality")}</span>
            </p>
          ) : null}
        </div>
      ) : null}

      {step === 3 && selected ? (
        <div className="space-y-3">
          {selected.category === "VISA" ? (
            <>
              <RadioGroup
                value={extra.passportOk === undefined ? "" : extra.passportOk ? "yes" : "no"}
                onValueChange={(value) => setExtra({ passportOk: value === "yes" })}
              >
                <WizardOption value="yes" selected={extra.passportOk === true} label={t("wizard.passport.yes")} />
                <WizardOption value="no" selected={extra.passportOk === false} label={t("wizard.passport.no")} />
              </RadioGroup>
              {extra.passportOk === false ? (
                <p className="flex items-start gap-2 text-body-sm text-destructive" role="status">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                  <span>{t("wizard.ineligible.passport")}</span>
                </p>
              ) : null}
            </>
          ) : null}
          {selected.category === "LEGALIZATION" ? (
            <RadioGroup value={extra.documentType ?? ""} onValueChange={(value) => setExtra({ documentType: value })}>
              {DOCUMENT_TYPES.map((item) => (
                <WizardOption
                  key={item.code}
                  value={item.code}
                  selected={extra.documentType === item.code}
                  label={t(item.hintKey)}
                  leading={documentLeading(item.code)}
                />
              ))}
            </RadioGroup>
          ) : null}
          {selected.category === "APOSTILLE" ? (
            <>
              <RadioGroup
                value={extra.hague === undefined ? "" : extra.hague ? "yes" : "no"}
                onValueChange={(value) => setExtra({ hague: value === "yes" })}
              >
                <WizardOption value="yes" selected={extra.hague === true} label={t("wizard.hague.yes")} />
                <WizardOption value="no" selected={extra.hague === false} label={t("wizard.hague.no")} />
              </RadioGroup>
              {extra.hague === false ? (
                <p className="flex items-start gap-2 text-body-sm text-destructive" role="status">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                  <span>{t("wizard.ineligible.hague")}</span>
                </p>
              ) : null}
            </>
          ) : null}
        </div>
      ) : null}

      {step === 4 && selected ? (
        <div className="space-y-4 rounded-lg border border-border bg-card p-6">
          <p className="text-body text-foreground">{loc(selected.nameI18n, i18n.language, selected.code)}</p>
          <p className="text-body-sm text-muted-foreground">
            {t("wizard.confirm.summary", {
              nationality: t(`wizard.nationality.${nationality}`),
              price: amount ?? t("wizard.confirm.priceLater"),
            })}
          </p>
          {defaultName ? (
            <p className="text-body-sm text-muted-foreground">
              {t("wizard.confirm.identity")}: {defaultName}
              {defaultEmail ? ` · ${defaultEmail}` : ""}
            </p>
          ) : null}
          {!eligible ? (
            <p className="flex items-start gap-2 text-body-sm text-destructive" role="alert">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{t("wizard.ineligible.generic")}</span>
            </p>
          ) : (
            <label className="flex items-start gap-2 text-body-sm">
              <input
                className="mt-1"
                type="checkbox"
                checked={consent}
                onChange={(event) => setConsent(event.target.checked)}
              />
              <span>{notice.data ? loc(notice.data.textI18n, i18n.language) : t("privacy.checkbox")}</span>
            </label>
          )}
          {errorKey ? (
            <p className="flex items-start gap-2 text-body-sm text-destructive" role="alert">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{t(errorKey, { defaultValue: t("common.error") })}</span>
            </p>
          ) : null}
        </div>
      ) : null}
    </WizardShell>
  );
}
