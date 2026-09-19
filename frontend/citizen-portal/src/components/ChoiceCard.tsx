import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

type Props = {
  title: string;
  description?: string;
  selected?: boolean;
  disabled?: boolean;
  onSelect: () => void;
};

export function ChoiceCard({ title, description, selected, disabled, onSelect }: Props) {
  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={disabled}
      aria-pressed={selected}
      className={cn(
        "flex w-full items-center justify-between gap-3 rounded-lg border p-4 text-left transition-colors",
        selected ? "border-primary bg-primary/5" : "border-border bg-card hover:bg-muted",
        disabled && "cursor-not-allowed opacity-50",
      )}
    >
      <span className="min-w-0">
        <span className="block font-medium text-foreground">{title}</span>
        {description ? <span className="mt-1 block text-body-sm text-muted-foreground">{description}</span> : null}
      </span>
      {selected ? (
        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Check className="h-4 w-4" aria-hidden />
        </span>
      ) : null}
    </button>
  );
}
