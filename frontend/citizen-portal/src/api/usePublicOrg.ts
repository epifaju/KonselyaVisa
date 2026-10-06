import { useQuery } from "@tanstack/react-query";
import { apiGet, type ApiResponse } from "@/api/client";
import { publicOrgSearchParams, rememberPublicOrganizationId } from "@/api/publicOrganization";

export type OrganizationBranding = {
  organizationId: string;
  nameI18n: Record<string, string>;
  defaultLocale: string;
  activeLanguages: string[];
  brandColor: string | null;
  domain: string | null;
  logoUrl: string | null;
  faviconUrl: string | null;
};

export function usePublicOrg() {
  const params = publicOrgSearchParams();
  const qs = params ? `?${params}` : "";
  return useQuery({
    queryKey: ["public-org", params || "default"],
    queryFn: async () => {
      const data = (await apiGet<ApiResponse<OrganizationBranding>>("", `/api/v1/public/org${qs}`)).data!;
      rememberPublicOrganizationId(data.organizationId);
      return data;
    },
  });
}
