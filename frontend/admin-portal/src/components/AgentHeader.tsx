import { useTranslation } from "react-i18next";
import { OrganizationBrand } from "@/components/OrganizationBrand";
import { Button } from "@/components/ui/button";

type Props = {
  organizationName: string;
  logoUrl?: string | null;
  languages: string[];
  loggedIn: boolean;
  onLogout: () => void;
};

export function AgentHeader({ organizationName, logoUrl, languages, loggedIn, onLogout }: Props) {
  const { t, i18n } = useTranslation();
  const codes = languages.length ? languages : ["fr", "pt", "en"];

  return (
    <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
      <OrganizationBrand layout="header" organizationName={organizationName} logoUrl={logoUrl} />
      <div className="ml-auto flex flex-wrap items-center gap-2">
        {codes.map((lng) => (
          <Button
            key={lng}
            type="button"
            size="sm"
            variant="ghost"
            aria-pressed={i18n.language.startsWith(lng)}
            onClick={() => void i18n.changeLanguage(lng)}
          >
            {t(`language.${lng}`, { defaultValue: lng.toUpperCase() })}
          </Button>
        ))}
        {loggedIn ? (
          <Button type="button" variant="outline" size="sm" onClick={onLogout}>
            {t("login.logout")}
          </Button>
        ) : null}
      </div>
    </header>
  );
}
