import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  apiGet,
  apiPatch,
  apiPost,
  loc,
  type ApiResponse,
  type CatalogProcedure,
  type CatalogRequirement,
  type CatalogVersion,
} from "@/api/client";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type Props = { token: string; procedureId: string; onBack: () => void };

type RequirementDraft = {
  code: string;
  required: boolean;
  fr: string;
  pt: string;
  en: string;
};

function toRequirementDrafts(items: CatalogRequirement[] | undefined): RequirementDraft[] {
  if (!items?.length) {
    return [{ code: "", required: true, fr: "", pt: "", en: "" }];
  }
  return items.map((item) => ({
    code: item.code ?? "",
    required: item.required !== false,
    fr: item.labelI18n?.fr ?? "",
    pt: item.labelI18n?.pt ?? "",
    en: item.labelI18n?.en ?? "",
  }));
}

function fromRequirementDrafts(items: RequirementDraft[]): CatalogRequirement[] {
  return items
    .filter((item) => item.code.trim())
    .map((item) => ({
      code: item.code.trim().toUpperCase(),
      required: item.required,
      labelI18n: { fr: item.fr.trim(), pt: item.pt.trim(), en: item.en.trim() },
    }));
}

function findDraft(versions: CatalogVersion[]) {
  return versions.find((version) => version.status === "DRAFT");
}

function versionBadge(status: string) {
  if (status === "PUBLISHED") return "success" as const;
  if (status === "DRAFT") return "warning" as const;
  return "secondary" as const;
}

function parsedDays(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isInteger(parsed) ? parsed : null;
}

const fieldClass = "mt-1 w-full rounded-md border bg-background px-2 py-1";

export function CatalogEditor({ token, procedureId, onBack }: Props) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [nameFr, setNameFr] = useState("");
  const [namePt, setNamePt] = useState("");
  const [nameEn, setNameEn] = useState("");
  const [descriptionFr, setDescriptionFr] = useState("");
  const [rulesText, setRulesText] = useState("{}");
  const [requirements, setRequirements] = useState<RequirementDraft[]>([]);
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [estimatedDays, setEstimatedDays] = useState("");

  const query = useQuery({
    queryKey: ["catalog-procedure", procedureId],
    queryFn: async () =>
      (await apiGet<ApiResponse<CatalogProcedure>>(token, `/api/v1/procedures/${procedureId}`)).data!,
  });

  const procedure = query.data;
  const draft = procedure ? findDraft(procedure.versions) : undefined;

  useEffect(() => {
    if (!procedure) return;
    setNameFr(procedure.nameI18n?.fr ?? "");
    setNamePt(procedure.nameI18n?.pt ?? "");
    setNameEn(procedure.nameI18n?.en ?? "");
    setDescriptionFr(procedure.descriptionI18n?.fr ?? "");
    const source = findDraft(procedure.versions) ?? procedure.versions.find((v) => v.status === "PUBLISHED");
    setRulesText(JSON.stringify(source?.eligibilityRules ?? {}, null, 2));
    setRequirements(toRequirementDrafts(source?.documentRequirements));
    setEstimatedDays(
      source?.estimatedInstructionDays != null ? String(source.estimatedInstructionDays) : "",
    );
    setJsonError(null);
  }, [procedure]);

  const parsedRules = useMemo(() => {
    try {
      const value = JSON.parse(rulesText) as unknown;
      if (!value || typeof value !== "object" || Array.isArray(value)) {
        return { ok: false as const, value: {} };
      }
      return { ok: true as const, value: value as Record<string, unknown> };
    } catch {
      return { ok: false as const, value: {} };
    }
  }, [rulesText]);

  const saveNames = useMutation({
    mutationFn: () =>
      apiPatch<ApiResponse<CatalogProcedure>>(token, `/api/v1/procedures/${procedureId}`, {
        nameI18n: { fr: nameFr, pt: namePt, en: nameEn },
        descriptionI18n: { fr: descriptionFr, pt: procedure?.descriptionI18n?.pt ?? "", en: procedure?.descriptionI18n?.en ?? "" },
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["catalog-procedure", procedureId] }),
  });

  const saveDraft = useMutation({
    mutationFn: async () => {
      if (!draft) throw new Error("no_draft");
      if (!parsedRules.ok) throw new Error("json");
      return apiPatch<ApiResponse<CatalogVersion>>(
        token,
        `/api/v1/procedures/${procedureId}/versions/${draft.id}`,
        {
          eligibilityRules: parsedRules.value,
          documentRequirements: fromRequirementDrafts(requirements),
          estimatedInstructionDays: parsedDays(estimatedDays),
        },
      );
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["catalog-procedure", procedureId] }),
  });

  const createDraft = useMutation({
    mutationFn: () => {
      const published = procedure?.versions.find((version) => version.status === "PUBLISHED");
      return apiPost<ApiResponse<CatalogVersion>>(token, `/api/v1/procedures/${procedureId}/versions`, {
        copyFromVersionId: published?.id ?? null,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["catalog-procedure", procedureId] });
      void queryClient.invalidateQueries({ queryKey: ["catalog-procedures"] });
    },
  });

  const publish = useMutation({
    mutationFn: async () => {
      if (!draft) throw new Error("no_draft");
      if (!parsedRules.ok) throw new Error("json");
      await apiPatch(token, `/api/v1/procedures/${procedureId}/versions/${draft.id}`, {
        eligibilityRules: parsedRules.value,
        documentRequirements: fromRequirementDrafts(requirements),
        estimatedInstructionDays: parsedDays(estimatedDays),
      });
      return apiPost<ApiResponse<CatalogVersion>>(
        token,
        `/api/v1/procedures/${procedureId}/versions/${draft.id}/publish`,
      );
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["catalog-procedure", procedureId] });
      void queryClient.invalidateQueries({ queryKey: ["catalog-procedures"] });
    },
  });

  const onSaveNames = (event: FormEvent) => {
    event.preventDefault();
    saveNames.mutate();
  };

  const updateRequirement = (index: number, patch: Partial<RequirementDraft>) => {
    setRequirements((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  if (query.isLoading) {
    return <p className="text-muted-foreground">{t("common.loading")}</p>;
  }
  if (query.isError || !procedure) {
    return (
      <p className="text-destructive" role="alert">
        {t("common.error")}
      </p>
    );
  }

  const busy = saveNames.isPending || saveDraft.isPending || createDraft.isPending || publish.isPending;

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Button type="button" variant="ghost" size="sm" onClick={onBack}>
            {t("common.back")}
          </Button>
          <h2 className="text-xl font-semibold">{loc(procedure.nameI18n, i18n.language)}</h2>
          <p className="text-body-sm text-muted-foreground">
            {procedure.code} · {procedure.category} · {loc(procedure.originCountry?.nameI18n, i18n.language)} →{" "}
            {loc(procedure.destinationCountry?.nameI18n, i18n.language)}
          </p>
        </div>
      </div>

      <p className="rounded-md border border-border bg-muted/40 px-3 py-2 text-body-sm text-muted-foreground" role="note">
        {t("catalog.freezeHint")}
      </p>

      <form onSubmit={onSaveNames} className="grid gap-3 rounded-lg border bg-card p-4 md:grid-cols-2">
        <h3 className="md:col-span-2 text-sm font-medium">{t("catalog.names")}</h3>
        <label className="text-sm">
          {t("catalog.nameFr")}
          <input required className={fieldClass} value={nameFr} onChange={(e) => setNameFr(e.target.value)} />
        </label>
        <label className="text-sm">
          {t("catalog.namePt")}
          <input className={fieldClass} value={namePt} onChange={(e) => setNamePt(e.target.value)} />
        </label>
        <label className="text-sm">
          {t("catalog.nameEn")}
          <input className={fieldClass} value={nameEn} onChange={(e) => setNameEn(e.target.value)} />
        </label>
        <label className="text-sm md:col-span-2">
          {t("catalog.descriptionFr")}
          <textarea className={fieldClass} rows={2} value={descriptionFr} onChange={(e) => setDescriptionFr(e.target.value)} />
        </label>
        <div className="md:col-span-2">
          <Button type="submit" variant="outline" size="sm" disabled={busy}>
            {t("catalog.saveNames")}
          </Button>
        </div>
      </form>

      <div className="rounded-lg border bg-card p-4">
        <h3 className="mb-3 text-sm font-medium">{t("catalog.versions")}</h3>
        <ul className="space-y-1 text-body-sm">
          {procedure.versions.map((version) => (
            <li key={version.id} className="flex items-center gap-2">
              <span>v{version.versionNumber}</span>
              <Badge variant={versionBadge(version.status)}>{t(`catalog.versionStatus.${version.status}`)}</Badge>
              {version.estimatedInstructionDays != null ? (
                <span className="text-muted-foreground">
                  {t("catalog.daysShort", { count: version.estimatedInstructionDays })}
                </span>
              ) : null}
            </li>
          ))}
        </ul>
      </div>

      {draft ? (
        <div className="space-y-4 rounded-lg border bg-card p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-medium">{t("catalog.draftTitle", { version: draft.versionNumber })}</h3>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={busy || !parsedRules.ok}
                onClick={() => {
                  if (!parsedRules.ok) {
                    setJsonError(t("catalog.jsonInvalid"));
                    return;
                  }
                  setJsonError(null);
                  saveDraft.mutate();
                }}
              >
                {t("catalog.saveDraft")}
              </Button>
              <Button
                type="button"
                size="sm"
                disabled={busy || !parsedRules.ok}
                onClick={() => {
                  if (!parsedRules.ok) {
                    setJsonError(t("catalog.jsonInvalid"));
                    return;
                  }
                  setJsonError(null);
                  publish.mutate();
                }}
              >
                {t("catalog.publish")}
              </Button>
            </div>
          </div>

          <div>
            <h4 className="mb-2 text-sm font-medium">{t("catalog.requirements")}</h4>
            <div className="space-y-2">
              {requirements.map((row, index) => (
                <div key={index} className="grid gap-2 rounded-md border p-2 md:grid-cols-6">
                  <label className="text-caption">
                    {t("catalog.reqCode")}
                    <input className={fieldClass} value={row.code} onChange={(e) => updateRequirement(index, { code: e.target.value })} />
                  </label>
                  <label className="flex items-end gap-2 text-caption">
                    <input
                      type="checkbox"
                      className="mb-2"
                      checked={row.required}
                      onChange={(e) => updateRequirement(index, { required: e.target.checked })}
                    />
                    {t("catalog.reqRequired")}
                  </label>
                  <label className="text-caption">
                    FR
                    <input className={fieldClass} value={row.fr} onChange={(e) => updateRequirement(index, { fr: e.target.value })} />
                  </label>
                  <label className="text-caption">
                    PT
                    <input className={fieldClass} value={row.pt} onChange={(e) => updateRequirement(index, { pt: e.target.value })} />
                  </label>
                  <label className="text-caption md:col-span-2">
                    EN
                    <input className={fieldClass} value={row.en} onChange={(e) => updateRequirement(index, { en: e.target.value })} />
                  </label>
                </div>
              ))}
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="mt-2"
              onClick={() => setRequirements((current) => [...current, { code: "", required: true, fr: "", pt: "", en: "" }])}
            >
              {t("catalog.addRequirement")}
            </Button>
          </div>

          <label className="block text-sm">
            {t("catalog.estimatedDays")}
            <input
              type="number"
              min={1}
              max={365}
              className={fieldClass}
              value={estimatedDays}
              onChange={(e) => setEstimatedDays(e.target.value)}
            />
          </label>
          <p className="text-caption text-muted-foreground">{t("catalog.estimatedDaysHint")}</p>

          <label className="block text-sm">
            {t("catalog.jsonLogic")}
            <textarea
              className={`${fieldClass} font-mono text-caption`}
              rows={10}
              value={rulesText}
              onChange={(e) => {
                setRulesText(e.target.value);
                setJsonError(null);
              }}
              aria-invalid={parsedRules.ok ? undefined : true}
            />
          </label>
          {!parsedRules.ok || jsonError ? (
            <p className="text-body-sm text-destructive" role="alert">
              {jsonError ?? t("catalog.jsonInvalid")}
            </p>
          ) : null}
        </div>
      ) : (
        <div className="rounded-lg border bg-card p-4">
          <p className="mb-3 text-body-sm text-muted-foreground">{t("catalog.noDraft")}</p>
          <Button type="button" size="sm" disabled={busy} onClick={() => createDraft.mutate()}>
            {t("catalog.newVersion")}
          </Button>
        </div>
      )}

      {saveNames.isError || saveDraft.isError || createDraft.isError || publish.isError ? (
        <p className="text-body-sm text-destructive" role="alert">
          {t("common.error")}
        </p>
      ) : null}
    </section>
  );
}
