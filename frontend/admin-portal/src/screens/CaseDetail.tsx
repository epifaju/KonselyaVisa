import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  apiGet,
  apiPost,
  downloadDocument,
  loc,
  type ApiResponse,
  type Appointment,
  type CaseDocument,
  type CaseItem,
  type Order,
} from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CaseHistory } from "@/screens/CaseHistory";
import { CorrectionPanel } from "@/screens/CorrectionPanel";

type Props = {
  token: string;
  caseId: string;
  onBack: () => void;
};

function documentBadge(status: string): "success" | "warning" | "destructive" | "outline" {
  if (status === "ACCEPTED") {
    return "success";
  }
  if (status === "UPLOADED" || status === "REPLACED") {
    return "warning";
  }
  if (status === "CORRECTION_REQUESTED" || status === "REJECTED") {
    return "destructive";
  }
  return "outline";
}

export function CaseDetail({ token, caseId, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [correctionDocId, setCorrectionDocId] = useState<string | null>(null);

  const caseQuery = useQuery({
    queryKey: ["case", caseId],
    queryFn: async () => (await apiGet<ApiResponse<CaseItem>>(token, `/api/v1/cases/${caseId}`)).data!,
  });
  const docsQuery = useQuery({
    queryKey: ["case-docs", caseId],
    queryFn: async () => (await apiGet<ApiResponse<CaseDocument[]>>(token, `/api/v1/cases/${caseId}/documents`)).data ?? [],
  });
  const ordersQuery = useQuery({
    queryKey: ["case-orders", caseId],
    queryFn: async () => (await apiGet<ApiResponse<Order[]>>(token, `/api/v1/cases/${caseId}/orders`)).data ?? [],
  });
  const appointmentsQuery = useQuery({
    queryKey: ["case-appointments", caseId],
    queryFn: async () =>
      (await apiGet<ApiResponse<Appointment[]>>(token, `/api/v1/cases/${caseId}/appointments`)).data ?? [],
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["case", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-docs", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-orders", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["case-history", caseId] });
    void queryClient.invalidateQueries({ queryKey: ["agent-cases"] });
  };

  const accept = useMutation({
    mutationFn: (documentId: string) => apiPost(token, `/api/v1/cases/${caseId}/documents/${documentId}/accept`),
    onSuccess: invalidate,
  });
  const correct = useMutation({
    mutationFn: ({ documentId, reason }: { documentId: string; reason: string }) =>
      apiPost(token, `/api/v1/cases/${caseId}/documents/${documentId}/request-correction`, { reason }),
    onSuccess: () => {
      setCorrectionDocId(null);
      invalidate();
    },
  });
  const confirmPayment = useMutation({
    mutationFn: (paymentId: string) => apiPost(token, `/api/v1/payments/${paymentId}/confirm-manual`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["case-orders", caseId] });
    },
  });

  const item = caseQuery.data;
  const factEntries = Object.entries(item?.applicantFacts ?? {}).filter(
    ([key]) => !key.toLowerCase().includes("passport") && !key.toLowerCase().includes("nationalid"),
  );

  return (
    <section className="space-y-6">
      <div className="flex items-center gap-3">
        <Button type="button" variant="outline" size="sm" onClick={onBack}>
          {t("common.back")}
        </Button>
        <div>
          <h2 className="text-heading-2 font-medium text-foreground">{item?.reference ?? t("common.loading")}</h2>
          {item ? (
            <p className="text-body-sm text-muted-foreground">
              {loc(item.procedureNameI18n, i18n.language, item.procedureCode)} ·{" "}
              {t(`status.case.${item.status}`)}
            </p>
          ) : null}
        </div>
      </div>
      {caseQuery.isError ? (
        <p className="flex items-center gap-2 text-body-sm text-destructive" role="alert">
          <AlertCircle className="h-4 w-4" aria-hidden />
          {t("common.error")}
        </p>
      ) : null}
      {item ? (
        <div className="grid gap-4 md:grid-cols-2">
          <article className="rounded-lg border border-border bg-card p-4">
            <h3 className="font-medium text-foreground">{t("case.applicant")}</h3>
            <p className="mt-2 text-body-sm">{item.applicant.displayName}</p>
            <p className="text-body-sm text-muted-foreground">{item.applicant.email}</p>
          </article>
          <article className="rounded-lg border border-border bg-card p-4">
            <h3 className="font-medium text-foreground">{t("case.facts")}</h3>
            <ul className="mt-2 space-y-1 text-body-sm">
              {factEntries.map(([key, value]) => (
                <li key={key}>
                  <span className="text-muted-foreground">{key}: </span>
                  {String(value)}
                </li>
              ))}
            </ul>
          </article>
        </div>
      ) : null}

      <article className="rounded-lg border border-border bg-card p-4">
        <h3 className="font-medium text-foreground">{t("case.documents")}</h3>
        <ul className="mt-3 space-y-3">
          {(docsQuery.data ?? []).map((doc) => (
            <li key={doc.id} className="rounded-md border border-border p-3 text-body-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-medium text-foreground">{doc.requirementCode}</p>
                  <p className="text-muted-foreground">
                    {doc.originalFilename}
                    {doc.duplicateHash ? ` · ${t("case.duplicate")}` : ""}
                  </p>
                </div>
                <Badge variant={documentBadge(doc.status)}>{t(`status.document.${doc.status}`)}</Badge>
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => void downloadDocument(token, caseId, doc.id, doc.originalFilename)}
                >
                  {t("case.download")}
                </Button>
                <Button type="button" size="sm" variant="success" onClick={() => accept.mutate(doc.id)}>
                  {t("case.accept")}
                </Button>
                <Button type="button" size="sm" variant="destructive" onClick={() => setCorrectionDocId(doc.id)}>
                  {t("case.correct")}
                </Button>
              </div>
              {correctionDocId === doc.id ? (
                <CorrectionPanel
                  requirementCode={doc.requirementCode}
                  busy={correct.isPending}
                  onCancel={() => setCorrectionDocId(null)}
                  onSubmit={(reason) => correct.mutate({ documentId: doc.id, reason })}
                />
              ) : null}
            </li>
          ))}
        </ul>
        {docsQuery.data?.length === 0 ? <p className="mt-2 text-body-sm text-muted-foreground">{t("case.noDocuments")}</p> : null}
      </article>

      <article className="rounded-lg border border-border bg-card p-4">
        <h3 className="font-medium text-foreground">{t("case.payments")}</h3>
        <ul className="mt-3 space-y-2 text-body-sm">
          {(ordersQuery.data ?? []).map((order) => (
            <li key={order.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border p-3">
              <span>
                {order.reference} — {(order.amountMinor / 100).toFixed(2)} {order.currency} —{" "}
                {t(`status.order.${order.status}`)}
              </span>
              {order.payment && (order.payment.status === "PENDING_MANUAL" || order.status === "PENDING_MANUAL") ? (
                <Button type="button" size="sm" onClick={() => confirmPayment.mutate(order.payment!.id)}>
                  {t("case.confirmPayment")}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
        {ordersQuery.data?.length === 0 ? <p className="mt-2 text-body-sm text-muted-foreground">{t("case.noOrders")}</p> : null}
      </article>

      <article className="rounded-lg border border-border bg-card p-4">
        <h3 className="font-medium text-foreground">{t("case.appointments")}</h3>
        <ul className="mt-3 space-y-2 text-body-sm">
          {(appointmentsQuery.data ?? []).map((appointment) => (
            <li key={appointment.id} className="rounded-md border border-border p-3">
              {t(`status.appointment.${appointment.status}`)} —{" "}
              {new Date(appointment.slot.startsAt).toLocaleString()}
            </li>
          ))}
        </ul>
        {appointmentsQuery.data?.length === 0 ? (
          <p className="mt-2 text-body-sm text-muted-foreground">{t("case.noAppointments")}</p>
        ) : null}
      </article>

      <CaseHistory token={token} caseId={caseId} />
    </section>
  );
}
