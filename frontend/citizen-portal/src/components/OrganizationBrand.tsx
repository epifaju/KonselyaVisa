import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";

type Props = {
  layout: "login" | "header";
  className?: string;
};

export function OrganizationBrand({ layout, className }: Props) {
  const { t } = useTranslation();
  const compact = layout === "header";

  return (
    <div className={cn(compact ? "flex items-center gap-3" : "flex flex-col items-center", className)}>
      <div className={cn("shrink-0 rounded-lg bg-primary", compact ? "h-10 w-10" : "mb-6 h-12 w-12")} aria-hidden />
      <div className={cn(compact ? "min-w-0" : "text-center")}>
        <p className={cn("font-medium text-foreground", compact ? "text-body" : "text-heading-2")}>{t("login.title")}</p>
        {compact ? (
          <p className="text-caption text-muted-foreground">{t("login.organizationName")}</p>
        ) : (
          <h1 className="mt-1 text-center text-body text-muted-foreground">{t("login.organizationName")}</h1>
        )}
      </div>
    </div>
  );
}
