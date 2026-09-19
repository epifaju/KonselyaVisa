import { useTranslation } from "react-i18next";
import type { Order } from "@/api/client";
import { Button } from "@/components/ui/button";

type Props = {
  order: Order | undefined;
  checkoutPending: boolean;
  payPending: boolean;
  onCheckout: () => void;
  onMockPay: (providerReference: string) => void;
  onStripeConfirm: (paymentId: string) => void;
};

export function JourneyPayment({
  order,
  checkoutPending,
  payPending,
  onCheckout,
  onMockPay,
  onStripeConfirm,
}: Props) {
  const { t } = useTranslation();
  const amount = formatAmount(order);

  if (!order) {
    return (
      <div className="space-y-3">
        <p className="text-body-sm text-muted-foreground">{t("journey.paymentHint")}</p>
        <Button type="button" className="w-full" disabled={checkoutPending} onClick={onCheckout}>
          {t("journey.checkout")}
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-body text-foreground">
        {amount} · {t(`status.order.${order.status}`, { defaultValue: order.status })}
      </p>
      <p className="text-body-sm text-muted-foreground">{t("journey.paymentHint")}</p>
      {order.payment?.status === "PENDING" && order.providerCode === "MOCK" ? (
        <Button
          type="button"
          className="w-full"
          disabled={payPending || !order.payment.providerReference}
          onClick={() => onMockPay(order.payment!.providerReference!)}
        >
          {t("journey.payAndSubmit")}
        </Button>
      ) : null}
      {order.payment?.status === "PENDING" && order.providerCode === "STRIPE" ? (
        <div className="flex flex-col gap-2">
          {order.payment.checkoutUrl?.startsWith("http") ? (
            <Button type="button" className="w-full" asChild>
              <a href={order.payment.checkoutUrl}>{t("journey.payAndSubmit")}</a>
            </Button>
          ) : null}
          <Button
            type="button"
            variant="outline"
            disabled={payPending || !order.payment.id}
            onClick={() => onStripeConfirm(order.payment!.id)}
          >
            {t("journey.stripeConfirm")}
          </Button>
        </div>
      ) : null}
      {order.status === "PENDING_MANUAL" ? (
        <p className="text-body-sm text-warning">{t("journey.manualPay")}</p>
      ) : null}
    </div>
  );
}

export function formatAmount(order: Order | undefined): string | null {
  if (!order) {
    return null;
  }
  return `${(order.amountMinor / 100).toFixed(2)} ${order.currency}`;
}
