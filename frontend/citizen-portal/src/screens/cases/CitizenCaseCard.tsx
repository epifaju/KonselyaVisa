import { CalendarDays, CheckCircle2, Clock, FileText, Wallet } from "lucide-react";
import { useTranslation } from "react-i18next";
import { loc, type CaseItem } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { shortCaseReference } from "@/lib/caseReference";
import { cn } from "@/lib/utils";

type Props = {
  item: CaseItem;
  onOpen: (id: string) => void;
};

export function CitizenCaseCard({ item, onOpen }: Props) {
  const { t, i18n } = useTranslation();
  const urgent = item.listUrgencyGroup === "ACTION_REQUIRED";
  const date = formatDate(item.createdAt, i18n.language);
  const days = item.estimatedInstructionDays;
  const subtitle = t(item.listSubtitleMessageKey ?? "cases.nextStep.fallback", {
    date,
    count: days ?? 0,
    defaultValue: t(item.nextActionMessageKey ?? "cases.nextStep.fallback"),
  });
  const delay =
    days != null && days > 0 ? t("cases.estimatedDays", { count: days }) : null;
  const action = t(item.listActionMessageKey ?? "cases.open", { defaultValue: t("cases.open") });

  return (
    <Card className={cn(urgent && "border-l-[3px] border-l-destructive")}>
      <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <StatusGlyph group={item.listUrgencyGroup} nextAction={item.nextAction} />
          <div className="min-w-0">
            <p className="font-medium text-foreground">{procedureTitle(item, i18n.language)}</p>
            <p className="text-body-sm text-muted-foreground">
              {t("cases.reference", { reference: shortCaseReference(item.reference) })}
              {` · ${subtitle}`}
              {delay ? ` · ${delay}` : ""}
            </p>
          </div>
        </div>
        <Button
          type="button"
          variant={urgent ? "destructive" : "outline"}
          className="shrink-0 self-start sm:self-center"
          onClick={() => onOpen(item.id)}
        >
          {action}
        </Button>
      </CardContent>
    </Card>
  );
}

function StatusGlyph({ group, nextAction }: { group?: string; nextAction?: string }) {
  const Icon =
    nextAction === "BOOK_APPOINTMENT"
      ? CalendarDays
      : nextAction === "PAY" || nextAction === "WAIT_MANUAL_PAYMENT"
        ? Wallet
        : nextAction === "WAIT_PROCESSING"
          ? Clock
          : group === "CLOSED"
            ? CheckCircle2
            : FileText;
  const tone =
    group === "ACTION_REQUIRED"
      ? "bg-destructive/10 text-destructive"
      : group === "IN_INSTRUCTION"
        ? "bg-warning/10 text-warning"
        : "bg-success/10 text-success";
  return (
    <span className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-md", tone)}>
      <Icon className="h-5 w-5" aria-hidden />
    </span>
  );
}

export function procedureTitle(item: CaseItem, language: string): string {
  const name = loc(item.procedureNameI18n, language, item.procedureCode);
  const destination = loc(item.destinationCountry?.nameI18n, language, "");
  if (!destination) {
    return name;
  }
  const destPattern = new RegExp(`\\s*${destination.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`, "i");
  const withoutDest = name.replace(destPattern, "").trim();
  return `${withoutDest || name} — ${destination}`;
}

function formatDate(value: string | undefined, language: string): string {
  if (!value) {
    return "";
  }
  return new Intl.DateTimeFormat(language, { day: "numeric", month: "short" }).format(new Date(value));
}
