import { useQuery } from "@tanstack/react-query";
import { ChevronDown, Languages } from "lucide-react";
import { useTranslation } from "react-i18next";
import { apiGet, loc, type ApiResponse } from "@/api/client";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { formalityIcon, reassuranceIcon, TrustShield } from "@/screens/public/publicIcons";

export type PublicSite = {
  organizationId: string;
  nameI18n: Record<string, string>;
  defaultLocale: string;
  activeLanguages: string[];
  addressI18n: Record<string, string>;
  openingHoursI18n: Record<string, string>;
  contactEmail?: string | null;
  contactPhone?: string | null;
  formalities: { category: string }[];
};

export const PUBLIC_ORG = import.meta.env.VITE_ORGANIZATION_ID ?? "11111111-1111-1111-1111-111111111111";

export function usePublicSite() {
  return useQuery({
    queryKey: ["public-site", PUBLIC_ORG],
    queryFn: async () =>
      (await apiGet<ApiResponse<PublicSite>>("", `/api/v1/public/site?organizationId=${PUBLIC_ORG}`)).data!,
  });
}

type Nav = "home" | "track" | "contact" | "legal" | "privacy";

type Props = {
  onStart: (category?: string) => void;
  onTrack: () => void;
  onSignIn: () => void;
  onNav: (view: Nav) => void;
};

export function PublicHome({ onStart, onTrack, onSignIn, onNav }: Props) {
  const { t, i18n } = useTranslation();
  const siteQuery = usePublicSite();
  const site = siteQuery.data;
  const orgName = loc(site?.nameI18n, i18n.language, t("login.organizationName"));
  const languages = site?.activeLanguages?.length ? site.activeLanguages : ["fr", "pt", "en"];
  const currentLang = languages.find((code) => i18n.language.startsWith(code)) ?? languages[0];
  const address = loc(site?.addressI18n, i18n.language, "");
  const hours = loc(site?.openingHoursI18n, i18n.language, "");
  const languageNames = languages.map((code) => t(`language.${code}`, { defaultValue: code.toUpperCase() }));

  return (
    <div className="login-theme min-h-screen bg-background text-foreground">
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-4 md:flex-row md:items-center md:justify-between md:px-6">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 shrink-0 rounded-lg bg-primary" aria-hidden />
            <div className="min-w-0">
              <p className="font-medium text-body text-foreground">{t("login.title")}</p>
              <p className="text-caption text-muted-foreground">{orgName}</p>
            </div>
          </div>
          <nav className="flex flex-wrap items-center gap-x-6 gap-y-2 text-body-sm" aria-label={t("home.nav.label")}>
            <a className="text-foreground underline-offset-4 hover:underline" href="#services" onClick={() => onNav("home")}>
              {t("home.nav.services")}
            </a>
            <button className="text-foreground underline-offset-4 hover:underline" type="button" onClick={onTrack}>
              {t("home.nav.track")}
            </button>
            <a className="text-foreground underline-offset-4 hover:underline" href="#contact" onClick={() => onNav("home")}>
              {t("home.nav.contact")}
            </a>
          </nav>
          <div className="flex items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button type="button" variant="outline" size="sm" className="gap-1" aria-label={t("language.label")}>
                  <Languages className="h-4 w-4" aria-hidden />
                  {t(`language.short.${currentLang}`, { defaultValue: currentLang.toUpperCase() })}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {languages.map((code) => (
                  <DropdownMenuItem key={code} onSelect={() => void i18n.changeLanguage(code)}>
                    {t(`language.${code}`, { defaultValue: code.toUpperCase() })}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
            <Button type="button" variant="outline" onClick={onSignIn}>
              {t("login.cta")}
            </Button>
          </div>
        </div>
      </header>

      <div className="bg-success/10 text-success">
        <p className="mx-auto flex max-w-6xl items-start gap-2 px-4 py-2 text-caption md:items-center md:px-6">
          <TrustShield className="mt-0.5 h-4 w-4 shrink-0 md:mt-0" />
          <span>{t("home.trust", { organization: orgName })}</span>
        </p>
      </div>

      <main>
        <section className="mx-auto max-w-3xl px-4 py-16 text-center md:px-6 md:py-20">
          <h1 className="text-heading-1 font-medium text-foreground">{t("home.hero.title")}</h1>
          <p className="mx-auto mt-4 max-w-xl text-body text-muted-foreground">{t("home.hero.subtitle")}</p>
          <div className="mt-8 flex flex-col items-stretch justify-center gap-3 sm:flex-row sm:items-center">
            <Button type="button" className="h-12 w-full rounded-md px-6 text-body sm:w-auto" onClick={() => onStart()}>
              {t("home.hero.start")}
            </Button>
            <Button type="button" variant="outline" className="h-12 w-full rounded-md px-6 text-body sm:w-auto" onClick={onTrack}>
              {t("home.hero.track")}
            </Button>
          </div>
          <div className="mt-10 flex justify-center text-muted-foreground">
            <a href="#how" aria-label={t("home.how.title")}>
              <ChevronDown className="h-6 w-6" />
            </a>
          </div>
        </section>

        <section id="services" className="mx-auto max-w-6xl scroll-mt-8 px-4 pb-16 md:px-6">
          <h2 className="mb-6 text-center text-body-sm text-muted-foreground">{t("home.formalities.title")}</h2>
          {siteQuery.isLoading ? <p className="text-center text-body-sm text-muted-foreground">{t("common.loading")}</p> : null}
          {siteQuery.isError ? (
            <p className="text-center text-body-sm text-destructive" role="alert">
              {t("common.error")}
            </p>
          ) : null}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            {(site?.formalities ?? []).map((item) => (
              <button
                key={item.category}
                type="button"
                className="flex min-h-[7.5rem] flex-col items-center justify-center gap-3 rounded-lg border border-border bg-card px-4 py-6 text-center text-body text-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                onClick={() => onStart(item.category)}
              >
                {formalityIcon(item.category)}
                <span>{t(`home.formality.${item.category}`, { defaultValue: item.category })}</span>
              </button>
            ))}
          </div>
        </section>

        <section id="how" className="bg-muted">
          <div className="mx-auto max-w-6xl px-4 py-16 md:px-6">
            <h2 className="mb-10 text-center text-heading-2 font-medium">{t("home.how.title")}</h2>
            <ol className="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-4">
              {(["eligibility", "documents", "pay", "follow"] as const).map((step, index) => (
                <li key={step} className="text-center">
                  <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-primary text-body font-medium text-primary-foreground">
                    {index + 1}
                  </span>
                  <p className="mt-4 font-medium text-body text-foreground">{t(`home.how.${step}.title`)}</p>
                  <p className="mt-1 text-body-sm text-muted-foreground">{t(`home.how.${step}.text`)}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-10 md:px-6">
          <ul className="flex flex-col items-center justify-center gap-6 text-body-sm text-muted-foreground md:flex-row md:gap-10">
            <li className="flex items-center gap-2">
              {reassuranceIcon("lock")}
              <span>{t("home.reassurance.secure")}</span>
            </li>
            <li className="flex items-center gap-2">
              {reassuranceIcon("shield")}
              <span>{t("home.reassurance.gdpr")}</span>
            </li>
            <li className="flex items-center gap-2">
              {reassuranceIcon("languages")}
              <span>{t("home.reassurance.languages", { languages: languageNames.join(", ") })}</span>
            </li>
          </ul>
        </section>
      </main>

      <footer id="contact" className="scroll-mt-8 border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 md:flex-row md:items-start md:justify-between md:px-6">
          <div>
            <p className="font-medium text-body text-foreground">{orgName}</p>
            {address ? <p className="mt-1 text-body-sm text-muted-foreground">{address}</p> : null}
            {hours ? <p className="text-body-sm text-muted-foreground">{hours}</p> : null}
            {site?.contactEmail ? (
              <p className="mt-1 text-body-sm text-muted-foreground">
                <a className="underline" href={`mailto:${site.contactEmail}`}>
                  {site.contactEmail}
                </a>
              </p>
            ) : null}
            {site?.contactPhone ? <p className="text-body-sm text-muted-foreground">{site.contactPhone}</p> : null}
          </div>
          <nav className="flex flex-wrap gap-x-6 gap-y-2 text-body-sm" aria-label={t("home.footer.links")}>
            <button className="underline-offset-4 hover:underline" type="button" onClick={() => onNav("legal")}>
              {t("home.footer.legal")}
            </button>
            <button className="underline-offset-4 hover:underline" type="button" onClick={() => onNav("privacy")}>
              {t("home.footer.privacy")}
            </button>
            <button className="underline-offset-4 hover:underline" type="button" onClick={() => onNav("contact")}>
              {t("home.nav.contact")}
            </button>
          </nav>
        </div>
      </footer>
    </div>
  );
}
