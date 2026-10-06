import { Globe } from "lucide-react";
import { useTranslation } from "react-i18next";
import { OrganizationBrand } from "@/components/OrganizationBrand";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type Props = {
  displayName: string;
  organizationName: string;
  logoUrl?: string | null;
  languages: string[];
  onCases: () => void;
  onPrivacy: () => void;
  onLogout: () => void;
};

export function CitizenHeader({
  displayName,
  organizationName,
  logoUrl,
  languages,
  onCases,
  onPrivacy,
  onLogout,
}: Props) {
  const { t, i18n } = useTranslation();
  const codes = languages.length ? languages : ["fr", "pt", "en"];
  const language = codes.find((code) => i18n.language.startsWith(code)) ?? codes[0];

  return (
    <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
      <OrganizationBrand layout="header" organizationName={organizationName} logoUrl={logoUrl} />
      <div className="flex items-center gap-3">
        <Button type="button" variant="outline" size="sm" onClick={onCases}>
          {t("cases.title")}
        </Button>
        <Select value={language} onValueChange={(value) => void i18n.changeLanguage(value)}>
          <SelectTrigger aria-label={t("language.label")} className="w-[5.5rem]">
            <Globe className="h-4 w-4 text-muted-foreground" aria-hidden />
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {codes.map((code) => (
              <SelectItem key={code} value={code}>
                {t(`language.short.${code}`, { defaultValue: code.toUpperCase() })}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <DropdownMenu>
          <DropdownMenuTrigger className="flex items-center gap-2 rounded-md px-1 py-1 text-left hover:bg-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-caption font-medium text-foreground">
              {initials(displayName)}
            </span>
            <span className="hidden text-body-sm font-medium text-foreground sm:inline">{displayName}</span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={onPrivacy}>{t("privacy.title")}</DropdownMenuItem>
            <DropdownMenuItem onSelect={onLogout}>{t("login.logout")}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "?";
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}
