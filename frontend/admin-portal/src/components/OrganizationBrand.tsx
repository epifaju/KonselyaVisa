import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

type Props = {
  layout: "login" | "header";
  organizationName: string;
  logoUrl?: string | null;
  className?: string;
};

export function OrganizationBrand({ layout, organizationName, logoUrl, className }: Props) {
  const { t } = useTranslation();
  const compact = layout === "header";
  const src = logoUrl ? `${API_BASE}${logoUrl}` : null;

  return (
    <div className={cn(compact ? "flex items-center gap-3" : "flex flex-col items-center", className)}>
      {src ? (
        <img
          src={src}
          alt=""
          className={cn("shrink-0 rounded-lg object-contain", compact ? "h-10 w-10" : "mb-6 h-12 w-12")}
        />
      ) : (
        <div className={cn("shrink-0 rounded-lg bg-primary", compact ? "h-10 w-10" : "mb-6 h-12 w-12")} aria-hidden />
      )}
      <div className={cn(compact ? "min-w-0" : "text-center")}>
        {compact ? (
          <>
            <p className="truncate text-body text-heading-3 font-medium text-foreground">{organizationName}</p>
            <p className="text-caption text-muted-foreground">{t("login.title")}</p>
          </>
        ) : (
          <>
            <h1 className="text-heading-2 font-medium text-foreground">{organizationName}</h1>
            <p className="mt-1 text-center text-caption text-muted-foreground">{t("login.title")}</p>
          </>
        )}
      </div>
    </div>
  );
}
