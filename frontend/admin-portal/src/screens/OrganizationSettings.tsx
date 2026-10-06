import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  apiDelete,
  apiGet,
  apiPatch,
  apiUpload,
  type ApiResponse,
  type OrganizationDetails,
} from "@/api/client";
import { usePublicOrg, type OrganizationBranding } from "@/api/usePublicOrg";
import { Button } from "@/components/ui/button";
import { useOrganizationBrandTheme } from "@/hooks/useOrganizationBrandTheme";

type Props = { token: string };

const PROVIDERS = ["MOCK", "MANUAL", "STRIPE", "CINETPAY", "PAYDUNYA"] as const;
const HEX = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export function OrganizationSettings({ token }: Props) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [brandColor, setBrandColor] = useState("");
  const [domain, setDomain] = useState("");
  const [paymentProvider, setPaymentProvider] = useState("MOCK");
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const orgQuery = useQuery({
    queryKey: ["organization-me"],
    queryFn: async () =>
      (await apiGet<ApiResponse<OrganizationDetails>>(token, "/api/v1/organizations/me")).data!,
  });
  const brandingQuery = usePublicOrg();

  useEffect(() => {
    const settings = orgQuery.data?.settings ?? {};
    setBrandColor(typeof settings.brandColor === "string" ? settings.brandColor : "");
    setDomain(typeof settings.domain === "string" ? settings.domain : "");
    setPaymentProvider(
      typeof settings.paymentProvider === "string" ? settings.paymentProvider : "MOCK",
    );
  }, [orgQuery.data]);

  useOrganizationBrandTheme(brandColor || null);

  const invalidateBranding = async () => {
    await queryClient.invalidateQueries({ queryKey: ["organization-me"] });
    await queryClient.invalidateQueries({ queryKey: ["me"] });
    await queryClient.invalidateQueries({ queryKey: ["public-org"] });
  };

  const save = useMutation({
    mutationFn: () =>
      apiPatch<ApiResponse<OrganizationDetails>>(token, "/api/v1/organizations/me/settings", {
        brandColor,
        domain,
        paymentProvider,
      }),
    onSuccess: async () => {
      setSaved(true);
      setFormError(null);
      await invalidateBranding();
    },
    onError: () => {
      setSaved(false);
      setFormError(t("orgSettings.saveError"));
    },
  });

  const uploadAsset = useMutation({
    mutationFn: ({ asset, file }: { asset: "logo" | "favicon"; file: File }) =>
      apiUpload<ApiResponse<OrganizationBranding>>(
        token,
        `/api/v1/organizations/me/branding/${asset}`,
        file,
      ),
    onSuccess: async () => {
      setFormError(null);
      await invalidateBranding();
    },
    onError: () => setFormError(t("orgSettings.assetError")),
  });

  const clearAsset = useMutation({
    mutationFn: (asset: "logo" | "favicon") =>
      apiDelete<ApiResponse<OrganizationBranding>>(token, `/api/v1/organizations/me/branding/${asset}`),
    onSuccess: async () => {
      setFormError(null);
      await invalidateBranding();
    },
    onError: () => setFormError(t("orgSettings.assetError")),
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSaved(false);
    if (brandColor.trim() && !HEX.test(brandColor.trim())) {
      setFormError(t("orgSettings.brandColorInvalid"));
      return;
    }
    if (domain.trim() && /[\s/]/.test(domain.trim())) {
      setFormError(t("orgSettings.domainInvalid"));
      return;
    }
    setFormError(null);
    save.mutate();
  };

  if (orgQuery.isLoading) {
    return <p className="text-muted-foreground">{t("common.loading")}</p>;
  }

  if (orgQuery.isError || !orgQuery.data) {
    return (
      <p className="text-body-sm text-destructive" role="alert">
        {t("common.error")}
      </p>
    );
  }

  const branding = brandingQuery.data;
  const busy = save.isPending || uploadAsset.isPending || clearAsset.isPending;

  return (
    <section className="space-y-6">
      <div>
        <h2 className="text-heading-2 font-medium text-foreground">{t("orgSettings.title")}</h2>
        <p className="mt-1 text-body-sm text-muted-foreground">{t("orgSettings.hint")}</p>
      </div>

      <form onSubmit={onSubmit} className="max-w-xl space-y-4 rounded-lg border border-border bg-card p-4">
        <label className="block text-body-sm">
          <span className="font-medium text-foreground">{t("orgSettings.brandColor")}</span>
          <span className="mt-0.5 block text-caption text-muted-foreground">
            {t("orgSettings.brandColorHint")}
          </span>
          <div className="mt-2 flex items-center gap-2">
            <input
              type="color"
              aria-label={t("orgSettings.brandColor")}
              className="h-10 w-12 cursor-pointer rounded-sm border border-border bg-background"
              value={HEX.test(brandColor) ? (brandColor.length === 4 ? expandShortHex(brandColor) : brandColor) : "#1780F2"}
              onChange={(event) => setBrandColor(event.target.value.toUpperCase())}
            />
            <input
              type="text"
              inputMode="text"
              placeholder="#1780F2"
              className="w-full rounded-sm border border-border bg-background px-2 py-2 text-body-sm"
              value={brandColor}
              onChange={(event) => setBrandColor(event.target.value)}
            />
          </div>
        </label>

        <AssetField
          label={t("orgSettings.logo")}
          hint={t("orgSettings.logoHint")}
          previewUrl={branding?.logoUrl ? `${API_BASE}${branding.logoUrl}` : null}
          disabled={busy}
          onUpload={(file) => uploadAsset.mutate({ asset: "logo", file })}
          onClear={() => clearAsset.mutate("logo")}
          clearLabel={t("orgSettings.clearAsset")}
          uploadLabel={t("orgSettings.uploadAsset")}
        />

        <AssetField
          label={t("orgSettings.favicon")}
          hint={t("orgSettings.faviconHint")}
          previewUrl={branding?.faviconUrl ? `${API_BASE}${branding.faviconUrl}` : null}
          disabled={busy}
          onUpload={(file) => uploadAsset.mutate({ asset: "favicon", file })}
          onClear={() => clearAsset.mutate("favicon")}
          clearLabel={t("orgSettings.clearAsset")}
          uploadLabel={t("orgSettings.uploadAsset")}
        />

        <label className="block text-body-sm">
          <span className="font-medium text-foreground">{t("orgSettings.domain")}</span>
          <span className="mt-0.5 block text-caption text-muted-foreground">{t("orgSettings.domainHint")}</span>
          <input
            type="text"
            placeholder="visa.acme.com"
            className="mt-2 w-full rounded-sm border border-border bg-background px-2 py-2 text-body-sm"
            value={domain}
            onChange={(event) => setDomain(event.target.value)}
          />
        </label>

        <label className="block text-body-sm">
          <span className="font-medium text-foreground">{t("orgSettings.paymentProvider")}</span>
          <span className="mt-0.5 block text-caption text-muted-foreground">
            {t("orgSettings.paymentProviderHint")}
          </span>
          <select
            className="mt-2 w-full rounded-sm border border-border bg-background px-2 py-2 text-body-sm"
            value={paymentProvider}
            onChange={(event) => setPaymentProvider(event.target.value)}
          >
            {PROVIDERS.map((code) => (
              <option key={code} value={code}>
                {t(`orgSettings.providers.${code}`, { defaultValue: code })}
              </option>
            ))}
          </select>
        </label>

        {formError ? (
          <p className="text-body-sm text-destructive" role="alert">
            {formError}
          </p>
        ) : null}
        {saved && !formError ? (
          <p className="text-body-sm text-success" role="status">
            {t("orgSettings.saved")}
          </p>
        ) : null}

        <Button type="submit" disabled={busy}>
          {save.isPending ? t("common.loading") : t("orgSettings.save")}
        </Button>
      </form>
    </section>
  );
}

function AssetField({
  label,
  hint,
  previewUrl,
  disabled,
  onUpload,
  onClear,
  clearLabel,
  uploadLabel,
}: {
  label: string;
  hint: string;
  previewUrl: string | null;
  disabled: boolean;
  onUpload: (file: File) => void;
  onClear: () => void;
  clearLabel: string;
  uploadLabel: string;
}) {
  return (
    <div className="block text-body-sm">
      <span className="font-medium text-foreground">{label}</span>
      <span className="mt-0.5 block text-caption text-muted-foreground">{hint}</span>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        {previewUrl ? (
          <img src={previewUrl} alt="" className="h-12 w-12 rounded-sm border border-border object-contain" />
        ) : (
          <div className="h-12 w-12 rounded-sm border border-dashed border-border bg-muted" aria-hidden />
        )}
        <label className="cursor-pointer">
          <span className="sr-only">{uploadLabel}</span>
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp,image/x-icon,.ico"
            className="text-body-sm"
            disabled={disabled}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                onUpload(file);
              }
              event.target.value = "";
            }}
          />
        </label>
        {previewUrl ? (
          <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={onClear}>
            {clearLabel}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function expandShortHex(hex: string): string {
  const h = hex.slice(1);
  return `#${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}`.toUpperCase();
}
