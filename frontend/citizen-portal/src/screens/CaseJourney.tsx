import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  ApiError,
  apiGet,
  apiPost,
  apiUpload,
  completeMockPayment,
  loc,
  publishedVersion,
  type ApiResponse,
  type Appointment,
  type AppointmentSlot,
  type CaseDocument,
  type CaseItem,
  type Order,
  type Procedure,
} from "@/api/client";
import { timelineStates } from "@/components/CaseTimeline";
import { JourneyStepper } from "@/components/JourneyStepper";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { shortCaseReference } from "@/lib/caseReference";
import { procedureTitle } from "@/screens/cases/CitizenCaseCard";
import { JourneyAppointment, appointmentLocation, slotHeadline } from "@/screens/journey/JourneyAppointment";
import { JourneyCurrentSection } from "@/screens/journey/JourneyCurrentSection";
import { JourneyDocuments, depositedCount } from "@/screens/journey/JourneyDocuments";
import { JourneyPayment, formatAmount } from "@/screens/journey/JourneyPayment";

type Props = {
  token: string;
  caseId: string;
  onBack: () => void;
};

export function CaseJourney({ token, caseId, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const lang = i18n.language;

  const caseQuery = useQuery({
    queryKey: ["case", caseId],
    queryFn: async () => (await apiGet<ApiResponse<CaseItem>>(token, `/api/v1/cases/${caseId}`)).data!,
    retry: 2,
  });
  const procedureQuery = useQuery({
    queryKey: ["procedure", caseQuery.data?.procedureDefinitionId],
    enabled: Boolean(caseQuery.data?.procedureDefinitionId),
    queryFn: async () =>
      (await apiGet<ApiResponse<Procedure>>(token, `/api/v1/procedures/${caseQuery.data!.procedureDefinitionId}`))
        .data!,
  });
  const docsQuery = useQuery({
    queryKey: ["case-docs", caseId],
    queryFn: async () =>
      (await apiGet<ApiResponse<CaseDocument[]>>(token, `/api/v1/cases/${caseId}/documents`)).data ?? [],
  });
  const ordersQuery = useQuery({
    queryKey: ["case-orders", caseId],
    queryFn: async () =>
      (await apiGet<ApiResponse<Order[]>>(token, `/api/v1/cases/${caseId}/orders`)).data ?? [],
  });
  const appointmentsQuery = useQuery({
    queryKey: ["case-appointments", caseId],
    queryFn: async () =>
      (await apiGet<ApiResponse<Appointment[]>>(token, `/api/v1/cases/${caseId}/appointments`)).data ?? [],
  });
  const slotsQuery = useQuery({
    queryKey: ["slots"],
    queryFn: async () =>
      (await apiGet<ApiResponse<AppointmentSlot[]>>(token, "/api/v1/appointment-slots")).data ?? [],
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["case", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-docs", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-orders", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-appointments", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["slots"] });
    void queryClient.invalidateQueries({ queryKey: ["my-cases"] });
  };

  const upload = useMutation({
    mutationFn: async ({ code, file }: { code: string; file: File }) => {
      const form = new FormData();
      form.append("requirementCode", code);
      form.append("file", file);
      return apiUpload(token, `/api/v1/cases/${caseId}/documents`, form);
    },
    onSuccess: refresh,
  });
  const checkout = useMutation({
    mutationFn: async () => {
      const response = await apiPost<ApiResponse<Order>>(token, `/api/v1/cases/${caseId}/orders`);
      const url = response.data?.payment?.checkoutUrl;
      if (response.data?.providerCode === "STRIPE" && url?.startsWith("http")) {
        window.location.assign(url);
      }
      return response;
    },
    onSuccess: refresh,
  });
  const mockPay = useMutation({
    mutationFn: (providerReference: string) => completeMockPayment(providerReference),
    onSuccess: refresh,
  });
  const syncPay = useMutation({
    mutationFn: (paymentId: string) => apiPost<ApiResponse<Order>>(token, `/api/v1/payments/${paymentId}/sync`),
    onSuccess: refresh,
  });
  const book = useMutation({
    mutationFn: async (slotId: string) => {
      await apiPost(token, `/api/v1/cases/${caseId}/appointment-holds`, { slotId });
      return apiPost(token, `/api/v1/cases/${caseId}/appointments`, { slotId });
    },
    onSuccess: refresh,
  });
  const cancelAppointment = useMutation({
    mutationFn: (appointmentId: string) => apiPost(token, `/api/v1/appointments/${appointmentId}/cancel`),
    onSuccess: refresh,
  });

  const item = caseQuery.data;
  const version =
    procedureQuery.data?.versions.find((candidate) => candidate.id === item?.procedureVersionId) ??
    publishedVersion(procedureQuery.data);
  const requirements = version?.documentRequirements ?? [];
  const docs = docsQuery.data ?? [];
  const order = ordersQuery.data?.[0];
  const booked = (appointmentsQuery.data ?? []).find((appointment) => appointment.status === "BOOKED");
  const slots = slotsQuery.data ?? [];
  const actionError =
    [upload, checkout, mockPay, syncPay, book, cancelAppointment].find((mutation) => mutation.error)?.error;
  const errorKey = actionError instanceof ApiError ? actionError.messageKey : undefined;
  const nextAction = item?.nextAction;
  const states = timelineStates(nextAction, item?.status ?? "");
  const currentIndex = states.indexOf("current");
  const shortRef = shortCaseReference(item?.reference);
  const openedAt = item?.createdAt
    ? new Intl.DateTimeFormat(lang, { dateStyle: "medium" }).format(new Date(item.createdAt))
    : null;
  const corridor = item ? corridorTitle(item, lang) : "";

  const docsContent = (
    <JourneyDocuments
      caseId={caseId}
      token={token}
      requirements={requirements}
      documents={docs}
      language={lang}
      busy={upload.isPending}
      allowUpload={currentIndex === 0}
      onUpload={(code, file) => upload.mutate({ code, file })}
    />
  );
  const paymentContent = (
    <JourneyPayment
      order={order}
      checkoutPending={checkout.isPending}
      payPending={mockPay.isPending || syncPay.isPending}
      onCheckout={() => checkout.mutate()}
      onMockPay={(reference) => mockPay.mutate(reference)}
      onStripeConfirm={(paymentId) => syncPay.mutate(paymentId)}
    />
  );
  const appointmentContent = (
    <JourneyAppointment
      booked={booked}
      slots={slots}
      language={lang}
      busy={book.isPending}
      allowCancel={currentIndex === 2}
      onBook={(slotId) => book.mutate(slotId)}
      onCancel={(appointmentId) => cancelAppointment.mutate(appointmentId)}
    />
  );

  const completed: { id: string; summary: string; body: ReactNode }[] = [];
  if (currentIndex > 0) {
    completed.push({
      id: "documents",
      summary: t("journey.done.documents", { count: depositedCount(docs, requirements) }),
      body: docsContent,
    });
  }
  if (currentIndex > 1) {
    completed.push({
      id: "payment",
      summary: t("journey.done.payment", { amount: formatAmount(order) ?? "—" }),
      body: paymentContent,
    });
  }
  if (currentIndex > 2 && booked) {
    completed.push({
      id: "appointment",
      summary: t("journey.done.appointment", { when: slotHeadline(booked.slot.startsAt, lang) }),
      body: appointmentContent,
    });
  }

  return (
    <section className="space-y-6">
      <nav aria-label={t("journey.breadcrumb")} className="text-body-sm text-muted-foreground">
        <button type="button" className="hover:text-foreground" onClick={onBack}>
          {t("cases.title")}
        </button>
        {shortRef ? <span> {">"} {shortRef}</span> : null}
      </nav>

      {item ? (
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-heading-2 font-medium text-foreground">{corridor}</h1>
            <p className="mt-1 text-body-sm text-muted-foreground">
              {t("cases.reference", { reference: shortRef })}
              {openedAt ? ` · ${t("journey.openedOn", { date: openedAt })}` : ""}
              {item.estimatedInstructionDays
                ? ` · ${t("journey.estimatedInstruction", { count: item.estimatedInstructionDays })}`
                : ""}
            </p>
          </div>
          {item.nextActionMessageKey ? (
            <span className="rounded-full bg-muted px-3 py-1 text-caption font-medium text-muted-foreground">
              {t(`journey.badge.${item.nextAction}`, {
                defaultValue: t(item.nextActionMessageKey, { defaultValue: t("cases.nextStep.fallback") }),
              })}
            </span>
          ) : null}
        </div>
      ) : (
        <p className="text-body-sm text-muted-foreground">{t("common.loading")}</p>
      )}

      {errorKey ? (
        <p className="flex items-center gap-2 text-body-sm text-destructive" role="alert">
          <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
          <span>{t(errorKey, { defaultValue: t("common.error") })}</span>
        </p>
      ) : null}

      {item ? (
        <JourneyStepper
          steps={[
            { id: "documents", label: t("journey.timeline.documents"), state: states[0] },
            { id: "payment", label: t("journey.timeline.payment"), state: states[1] },
            { id: "appointment", label: t("journey.timeline.appointment"), state: states[2] },
            { id: "done", label: t("journey.timeline.done"), state: states[3] },
          ]}
        />
      ) : null}

      {currentIndex === 0 ? (
        <JourneyCurrentSection title={t("journey.documents")}>{docsContent}</JourneyCurrentSection>
      ) : null}
      {currentIndex === 1 ? (
        <JourneyCurrentSection title={t("journey.payment")}>{paymentContent}</JourneyCurrentSection>
      ) : null}
      {currentIndex === 2 ? (
        <JourneyCurrentSection
          title={t("journey.bookDepositTitle")}
          subtitle={appointmentLocation(slots, lang)}
        >
          {appointmentContent}
        </JourneyCurrentSection>
      ) : null}
      {item && currentIndex === 3 ? (
        <JourneyCurrentSection title={t("journey.instructionTitle")}>
          <p className="text-body-sm text-muted-foreground">
            {item.estimatedInstructionDays
              ? t("journey.estimatedInstruction", { count: item.estimatedInstructionDays })
              : t("case.next_action.WAIT_PROCESSING")}
          </p>
        </JourneyCurrentSection>
      ) : null}

      {completed.length > 0 ? (
        <Accordion type="multiple" className="space-y-2">
          {completed.map((entry) => (
            <AccordionItem key={entry.id} value={entry.id}>
              <AccordionTrigger>
                <span className="flex min-w-0 items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden />
                  <span>{entry.summary}</span>
                </span>
              </AccordionTrigger>
              <AccordionContent>{entry.body}</AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      ) : null}
    </section>
  );
}

function corridorTitle(item: CaseItem, language: string): string {
  const origin = loc(item.originCountry?.nameI18n, language, "");
  const destination = loc(item.destinationCountry?.nameI18n, language, "");
  const name = loc(item.procedureNameI18n, language, item.procedureCode);
  if (origin && destination) {
    const stripped = name.replace(new RegExp(`\\s*[—-]\\s*${escapeReg(destination)}\\s*$`, "i"), "").trim();
    return `${stripped || name} — ${origin} → ${destination}`;
  }
  return procedureTitle(item, language);
}

function escapeReg(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
