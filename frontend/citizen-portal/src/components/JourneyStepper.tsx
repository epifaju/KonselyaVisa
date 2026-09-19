import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import type { TimelineStep } from "@/components/CaseTimeline";

type Props = {
  steps: TimelineStep[];
};

export function JourneyStepper({ steps }: Props) {
  return (
    <nav aria-label="Progression du dossier">
      <ol className="flex items-start">
        {steps.map((step, index) => (
          <li key={step.id} className="flex min-w-0 flex-1 flex-col items-center">
            <div className="flex w-full items-center">
              <span className={cn("h-0.5 flex-1", index === 0 ? "bg-transparent" : connectorClass(steps[index - 1]))} />
              <span
                className={cn(
                  "flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-caption font-medium",
                  step.state === "complete" && "bg-primary text-primary-foreground",
                  step.state === "current" && "bg-primary text-primary-foreground ring-4 ring-primary/15",
                  step.state === "upcoming" && "border border-border bg-background text-muted-foreground",
                )}
                aria-current={step.state === "current" ? "step" : undefined}
              >
                {step.state === "complete" ? <Check className="h-4 w-4" aria-hidden /> : null}
              </span>
              <span className={cn("h-0.5 flex-1", index === steps.length - 1 ? "bg-transparent" : connectorClass(step))} />
            </div>
            <span
              className={cn(
                "mt-2 text-center text-caption",
                step.state === "upcoming" ? "text-muted-foreground" : "font-medium text-foreground",
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

function connectorClass(step: TimelineStep): string {
  return step.state === "upcoming" ? "bg-border" : "bg-primary";
}
