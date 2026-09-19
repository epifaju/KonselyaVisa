import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

export type TimelineStepState = "complete" | "current" | "upcoming";

export type TimelineStep = {
  id: string;
  label: string;
  state: TimelineStepState;
};

type Props = {
  steps: TimelineStep[];
  caption?: string;
};

export function CaseTimeline({ steps, caption }: Props) {
  return (
    <nav aria-label="Progression du dossier" className="rounded-lg border border-border bg-card p-4">
      {caption ? <p className="mb-3 text-body-sm text-muted-foreground">{caption}</p> : null}
      <ol className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        {steps.map((step, index) => (
          <li key={step.id} className="flex min-w-0 flex-1 items-start gap-2">
            <span
              className={cn(
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-caption font-medium",
                step.state === "complete" && "bg-success/10 text-success",
                step.state === "current" && "bg-primary text-primary-foreground",
                step.state === "upcoming" && "bg-muted text-muted-foreground",
              )}
              aria-current={step.state === "current" ? "step" : undefined}
            >
              {step.state === "complete" ? <Check className="h-4 w-4" aria-hidden /> : index + 1}
            </span>
            <span
              className={cn(
                "pt-1 text-body-sm",
                step.state === "current" && "font-medium text-foreground",
                step.state === "complete" && "text-success",
                step.state === "upcoming" && "text-muted-foreground",
              )}
            >
              {step.label}
            </span>
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function timelineStates(nextAction: string | undefined, status: string): TimelineStepState[] {
  if (status === "COMPLETED") {
    return ["complete", "complete", "complete", "complete"];
  }
  if (status === "CANCELLED") {
    return ["upcoming", "upcoming", "upcoming", "upcoming"];
  }
  const current =
    nextAction === "PAY" || nextAction === "WAIT_MANUAL_PAYMENT"
      ? 1
      : nextAction === "BOOK_APPOINTMENT"
        ? 2
        : nextAction === "WAIT_PROCESSING" || nextAction === "NONE"
          ? 3
          : 0;
  return [0, 1, 2, 3].map((index) =>
    index < current ? "complete" : index === current ? "current" : "upcoming",
  );
}
