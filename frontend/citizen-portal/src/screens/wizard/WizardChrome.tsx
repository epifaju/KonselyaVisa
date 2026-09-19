import { FileText, GraduationCap, Plane, Scale, ScrollText, X } from "lucide-react";
import type { ReactNode } from "react";
import { RadioGroup } from "@/components/ui/radio-group";
import { WizardOption } from "@/components/WizardOption";
import { Button } from "@/components/ui/button";
import { flagEmoji } from "@/lib/flagEmoji";

type ShellProps = {
  progressLabel: string;
  flowLabel: string;
  question: string;
  hint?: string;
  showProgress: boolean;
  progressRatio: number;
  quitLabel: string;
  onQuit: () => void;
  quitConfirm?: boolean;
  quitConfirmText?: string;
  stayLabel?: string;
  leaveLabel?: string;
  onStay?: () => void;
  onLeave?: () => void;
  backLabel: string;
  continueLabel: string;
  backDisabled: boolean;
  continueDisabled: boolean;
  continuePending?: boolean;
  onBack: () => void;
  onContinue: () => void;
  children: ReactNode;
};

export function WizardShell({
  progressLabel,
  flowLabel,
  question,
  hint,
  showProgress,
  progressRatio,
  quitLabel,
  onQuit,
  quitConfirm,
  quitConfirmText,
  stayLabel,
  leaveLabel,
  onStay,
  onLeave,
  backLabel,
  continueLabel,
  backDisabled,
  continueDisabled,
  continuePending,
  onBack,
  onContinue,
  children,
}: ShellProps) {
  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <Button type="button" variant="outline" size="sm" className="gap-1 rounded-full" onClick={onQuit}>
          <X className="h-4 w-4" aria-hidden />
          {quitLabel}
        </Button>
        {showProgress ? <p className="text-body-sm text-muted-foreground">{progressLabel}</p> : null}
      </div>
      {quitConfirm ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted p-3">
          <p className="text-body-sm text-foreground">{quitConfirmText}</p>
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onStay}>
              {stayLabel}
            </Button>
            <Button type="button" variant="destructive" size="sm" onClick={onLeave}>
              {leaveLabel}
            </Button>
          </div>
        </div>
      ) : null}
      {showProgress ? (
        <div className="h-1 overflow-hidden rounded-sm bg-muted" aria-hidden>
          <div className="h-full bg-primary" style={{ width: `${Math.round(progressRatio * 100)}%` }} />
        </div>
      ) : null}
      <div>
        <p className="text-caption font-medium uppercase tracking-wide text-muted-foreground">{flowLabel}</p>
        <h1 className="mt-1 text-heading-1 font-medium text-foreground">{question}</h1>
        {hint ? <p className="mt-2 text-body-sm text-muted-foreground">{hint}</p> : null}
      </div>
      {children}
      <div className="flex items-center justify-between gap-3 pt-2">
        <Button type="button" variant="outline" disabled={backDisabled} onClick={onBack}>
          {backLabel}
        </Button>
        <Button
          type="button"
          disabled={continueDisabled || continuePending}
          className="disabled:cursor-not-allowed disabled:pointer-events-auto"
          onClick={onContinue}
        >
          {continueLabel}
        </Button>
      </div>
    </section>
  );
}

export function CountryFlag({ isoCode }: { isoCode: string }) {
  return <span className="text-heading-2">{flagEmoji(isoCode)}</span>;
}

export function procedureLeading(category: string): ReactNode {
  const className = "h-5 w-5 text-muted-foreground";
  if (category === "VISA") {
    return <Plane className={className} aria-hidden />;
  }
  if (category === "APOSTILLE") {
    return <ScrollText className={className} aria-hidden />;
  }
  if (category === "LEGALIZATION") {
    return <Scale className={className} aria-hidden />;
  }
  return <FileText className={className} aria-hidden />;
}

export function documentLeading(code: string): ReactNode {
  const className = "h-5 w-5 text-muted-foreground";
  if (code === "DIPLOMA") {
    return <GraduationCap className={className} aria-hidden />;
  }
  return <FileText className={className} aria-hidden />;
}

export { RadioGroup, WizardOption };
