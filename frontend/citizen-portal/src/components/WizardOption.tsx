import * as RadioGroupPrimitive from "@radix-ui/react-radio-group";
import { Check, Circle } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Props = {
  value: string;
  selected: boolean;
  label: string;
  description?: string;
  leading?: ReactNode;
};

export function WizardOption({ value, selected, label, description, leading }: Props) {
  return (
    <RadioGroupPrimitive.Item value={value} asChild>
      <button
        type="button"
        className={cn(
          "flex w-full cursor-pointer items-center gap-3 rounded-lg border p-4 text-left transition-colors",
          selected ? "border-primary bg-primary/10" : "border-border bg-card hover:bg-muted",
        )}
      >
        <span
          className={cn(
            "flex h-4 w-4 shrink-0 items-center justify-center rounded-full border",
            selected ? "border-primary" : "border-muted-foreground",
          )}
          aria-hidden
        >
          {selected ? <Circle className="h-2.5 w-2.5 fill-primary text-primary" /> : null}
        </span>
        {leading ? <span className="shrink-0 text-heading-2 leading-none">{leading}</span> : null}
        <span className="min-w-0 flex-1">
          <span className="block font-medium text-foreground">{label}</span>
          {description ? <span className="mt-1 block text-body-sm text-muted-foreground">{description}</span> : null}
        </span>
        {selected ? (
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <Check className="h-4 w-4" aria-hidden />
          </span>
        ) : null}
      </button>
    </RadioGroupPrimitive.Item>
  );
}
