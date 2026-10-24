import { cn } from "@/lib/utils";

type Size = "sm" | "md" | "lg";
type Variant = "default" | "onBrand";

type Props = {
  size?: Size;
  variant?: Variant;
  className?: string;
};

const SIZE: Record<Size, string> = {
  sm: "h-10 w-10",
  md: "h-12 w-12",
  lg: "h-14 w-14",
};

/** Neutral institutional mark when the org has not uploaded a logo. */
export function OrganizationLogoMark({ size = "md", variant = "default", className }: Props) {
  const onBrand = variant === "onBrand";
  return (
    <div
      className={cn(
        "flex shrink-0 items-center justify-center rounded-lg",
        SIZE[size],
        onBrand
          ? "bg-primary-foreground/95 text-primary shadow-sm"
          : "border border-primary/25 bg-primary/10 text-primary",
        className,
      )}
      aria-hidden
    >
      <svg viewBox="0 0 32 32" className="h-[58%] w-[58%]" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M16 3.5 6.5 7.2v7.1c0 6.4 4.1 10.8 9.5 12.7 5.4-1.9 9.5-6.3 9.5-12.7V7.2L16 3.5Z"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinejoin="round"
        />
        <path
          d="M11.2 16.1h9.6M16 11.3v9.6"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}
