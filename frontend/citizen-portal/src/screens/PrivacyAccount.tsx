import { useMutation, useQuery } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiError, apiGet, apiPost, loc, type ApiResponse } from "@/api/client";
import { Button } from "@/components/ui/button";

type PrivacyNotice = { version: string; textI18n: Record<string, string> };
type Deletion = { id: string; status: string; requestedAt: string; scheduledAnonymizeAt: string };

type Props = {
  token: string;
  onBack: () => void;
};

export function PrivacyAccount({ token, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const [exported, setExported] = useState(false);

  const notice = useQuery({
    queryKey: ["privacy-notice"],
    queryFn: async () => (await apiGet<ApiResponse<PrivacyNotice>>(token, "/api/v1/privacy-notices/current")).data!,
  });
  const exportData = useMutation({
    mutationFn: () => apiGet<ApiResponse<unknown>>(token, "/api/v1/me/data-export"),
    onSuccess: async (response) => {
      const blob = new Blob([JSON.stringify(response.data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "konselyavisa-data-export.json";
      link.click();
      URL.revokeObjectURL(url);
      setExported(true);
    },
  });
  const deletion = useMutation({
    mutationFn: () => apiPost<ApiResponse<Deletion>>(token, "/api/v1/me/data-deletion-request"),
  });

  const onExport = (event: FormEvent) => {
    event.preventDefault();
    exportData.mutate();
  };

  const errorKey =
    exportData.error instanceof ApiError
      ? exportData.error.messageKey
      : deletion.error instanceof ApiError
        ? deletion.error.messageKey
        : undefined;

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-3">
        <Button type="button" variant="outline" size="sm" onClick={onBack}>
          {t("common.back")}
        </Button>
        <h2 className="text-heading-1 font-medium text-foreground">{t("privacy.title")}</h2>
      </div>
      <p className="text-body-sm text-muted-foreground">
        {notice.data ? loc(notice.data.textI18n, i18n.language) : t("common.loading")}
      </p>
      {errorKey ? (
        <p className="text-body-sm text-destructive">{t(errorKey, { defaultValue: t("common.error") })}</p>
      ) : null}
      <form className="flex flex-wrap gap-2" onSubmit={onExport}>
        <Button type="submit" variant="outline" disabled={exportData.isPending}>
          {t("privacy.export")}
        </Button>
        <Button type="button" variant="outline" disabled={deletion.isPending} onClick={() => deletion.mutate()}>
          {t("privacy.requestDeletion")}
        </Button>
      </form>
      {exported ? <p className="text-body-sm text-muted-foreground">{t("privacy.exportDone")}</p> : null}
      {deletion.data?.data ? (
        <p className="text-body-sm text-muted-foreground">
          {t("privacy.deletionScheduled", { date: deletion.data.data.scheduledAnonymizeAt.slice(0, 10) })}
        </p>
      ) : null}
    </section>
  );
}
