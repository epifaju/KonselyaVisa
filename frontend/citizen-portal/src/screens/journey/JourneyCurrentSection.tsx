import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Props = {
  title: string;
  subtitle?: string;
  children: ReactNode;
};

export function JourneyCurrentSection({ title, subtitle, children }: Props) {
  return (
    <article className="rounded-lg border border-primary bg-card p-5">
      <h2 className="text-heading-3 font-medium text-foreground">{title}</h2>
      {subtitle ? <p className="mt-1 text-body-sm text-muted-foreground">{subtitle}</p> : null}
      <div className={cn("mt-4 space-y-4")}>{children}</div>
    </article>
  );
}
