export type ApiResponse<T> = {
  success: boolean;
  messageKey?: string;
  data?: T;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type SupervisorSummary = {
  openCount: number;
  watchCount: number;
  overdueCount: number;
};

export type MeResponse = {
  username: string;
  roles: string[];
  organizationId: string | null;
  organization?: {
    organizationId: string;
    nameI18n: Record<string, string>;
    defaultLocale: string;
    activeLanguages: string[];
    brandColor: string | null;
    domain: string | null;
    logoUrl?: string | null;
    faviconUrl?: string | null;
  } | null;
};

export type OrganizationDetails = {
  id: string;
  code: string;
  slug: string;
  nameI18n: Record<string, string>;
  status: string;
  defaultLocale: string;
  defaultCurrency: string;
  settings: Record<string, unknown>;
};

export type Applicant = {
  id: string;
  email: string | null;
  displayName: string | null;
  keycloakSubject: string | null;
  facts: Record<string, unknown>;
};

export type CaseItem = {
  id: string;
  reference: string;
  status: string;
  organizationId: string;
  applicant: Applicant;
  procedureDefinitionId: string;
  procedureCode: string;
  procedureNameI18n?: Record<string, string>;
  originCountry?: { isoCode: string; nameI18n: Record<string, string> };
  destinationCountry?: { isoCode: string; nameI18n: Record<string, string> };
  procedureVersionId: string;
  procedureVersionNumber: number;
  applicantFacts: Record<string, unknown>;
  eligibilityPassed: boolean;
  createdAt?: string;
  updatedAt?: string;
  nextAction?: string;
  nextActionMessageKey?: string;
  correctionMessageKey?: string | null;
  estimatedInstructionDays?: number | null;
  duplicateDocumentHash?: boolean;
};

export type CaseDocument = {
  id: string;
  caseId: string;
  requirementCode: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: string;
  duplicateHash: boolean;
  reviewMessageKey?: string | null;
  extractedFields?: Record<string, string>;
  documentValidations?: Record<string, string>;
  aiConfidence?: number | null;
  extractionSource?: string | null;
  createdAt: string;
};

export type DocumentHashMatch = {
  caseId: string;
  caseReference: string;
  applicantDisplayName: string;
  requirementCode: string;
};

export type DocumentHashAlert = {
  documentId: string;
  requirementCode: string;
  matches: DocumentHashMatch[];
};

export type DocumentUploadRefusal = {
  id: string;
  caseId: string;
  requirementCode: string | null;
  reasonKey: string;
  contentType: string | null;
  sizeBytes: number | null;
  originalFilename: string | null;
  attemptedAt: string;
  attemptedBy: string | null;
};

export type Payment = {
  id: string;
  providerCode: string;
  providerReference: string | null;
  status: string;
  currency: string;
  amountMinor: number;
  checkoutUrl: string | null;
};

export type Order = {
  id: string;
  caseId: string;
  reference: string;
  currency: string;
  amountMinor: number;
  status: string;
  providerCode: string;
  payment: Payment | null;
};

export type AppointmentSlot = {
  id: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  remainingCapacity: number;
  locationI18n: Record<string, string>;
  status: string;
};

export type Appointment = {
  id: string;
  caseId: string;
  status: string;
  slot: AppointmentSlot;
};

export type CaseHistoryEvent = {
  id: string;
  eventType: string;
  occurredAt: string;
  actorKind: string;
  messageKey: string | null;
  requirementCode: string | null;
};

export type CatalogCountry = {
  id: string;
  isoCode: string;
  nameI18n: Record<string, string>;
};

export type CatalogRequirement = {
  code: string;
  required?: boolean;
  labelI18n?: Record<string, string>;
};

export type CatalogVersion = {
  id: string;
  versionNumber: number;
  status: string;
  estimatedInstructionDays?: number | null;
  eligibilityRules: Record<string, unknown>;
  documentRequirements: CatalogRequirement[];
  pricing?: Record<string, unknown>;
  publishedAt?: string | null;
};

export type CatalogProcedure = {
  id: string;
  code: string;
  category: string;
  active: boolean;
  nameI18n: Record<string, string>;
  descriptionI18n: Record<string, string>;
  originCountry: CatalogCountry;
  destinationCountry: CatalogCountry;
  publishedVersionNumber: number | null;
  versions: CatalogVersion[];
};

export function loc(map: Record<string, string> | undefined, language: string, fallback = "—"): string {
  const lang = language.slice(0, 2);
  return map?.[lang] || map?.fr || map?.en || map?.pt || fallback;
}

const base = import.meta.env.VITE_API_BASE_URL ?? "";

export async function apiGet<T>(token: string, path: string): Promise<T> {
  return apiJson<T>(token, path, { method: "GET" });
}

export async function apiPost<T>(token: string, path: string, body?: unknown): Promise<T> {
  return apiJson<T>(token, path, {
    method: "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function apiPatch<T>(token: string, path: string, body?: unknown): Promise<T> {
  return apiJson<T>(token, path, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
}

export async function apiUpload<T>(token: string, path: string, file: File, fieldName = "file"): Promise<T> {
  const body = new FormData();
  body.append(fieldName, file);
  return apiJson<T>(token, path, { method: "POST", body });
}

export async function apiDelete<T>(token: string, path: string): Promise<T> {
  return apiJson<T>(token, path, { method: "DELETE" });
}

async function apiJson<T>(token: string, path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    throw new Error(`http_${response.status}`);
  }
  return (await response.json()) as T;
}

export async function downloadDocument(token: string, caseId: string, documentId: string, filename: string) {
  const response = await fetch(`${base}/api/v1/cases/${caseId}/documents/${documentId}/content`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`http_${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
