export type ApiResponse<T> = {
  success: boolean;
  messageKey?: string;
  data?: T;
};

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
};

export class ApiError extends Error {
  constructor(
    public status: number,
    public messageKey?: string,
  ) {
    super(messageKey ?? `http_${status}`);
  }
}

export type Me = {
  username: string;
  roles: string[];
  organizationId: string | null;
};

export type Procedure = {
  id: string;
  code: string;
  category: string;
  nameI18n: Record<string, string>;
  descriptionI18n: Record<string, string>;
  originCountry: { isoCode: string; nameI18n: Record<string, string> };
  destinationCountry: { isoCode: string; nameI18n: Record<string, string> };
  publishedVersionNumber: number | null;
  versions: ProcedureVersion[];
};

export type ProcedureVersion = {
  id: string;
  versionNumber: number;
  status: string;
  documentRequirements: DocumentRequirement[];
  pricing: { currency?: string; amountMinor?: number };
  estimatedInstructionDays?: number | null;
};

export type DocumentRequirement = {
  code: string;
  required?: boolean;
  labelI18n?: Record<string, string>;
};

export type CaseItem = {
  id: string;
  reference: string;
  status: string;
  procedureDefinitionId: string;
  procedureCode: string;
  procedureNameI18n?: Record<string, string>;
  originCountry?: { isoCode: string; nameI18n: Record<string, string> };
  destinationCountry?: { isoCode: string; nameI18n: Record<string, string> };
  procedureVersionId: string;
  procedureVersionNumber: number;
  applicant: { displayName: string | null; email: string | null };
  applicantFacts: Record<string, unknown>;
  eligibilityPassed: boolean;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string | null;
  createdByRole?: string | null;
  createdByLabel?: string | null;
  nextAction?: string;
  nextActionMessageKey?: string;
  correctionMessageKey?: string | null;
  listUrgencyGroup?: "ACTION_REQUIRED" | "IN_INSTRUCTION" | "CLOSED";
  listActionMessageKey?: string;
  listSubtitleMessageKey?: string;
  estimatedInstructionDays?: number | null;
};

export type CaseDocument = {
  id: string;
  requirementCode: string;
  originalFilename: string;
  status: string;
  duplicateHash: boolean;
  reviewMessageKey?: string | null;
  createdAt?: string;
};

export type Order = {
  id: string;
  reference: string;
  currency: string;
  amountMinor: number;
  status: string;
  providerCode: string;
  payment: {
    id: string;
    providerCode: string;
    providerReference: string | null;
    status: string;
    checkoutUrl: string | null;
  } | null;
};

export type AppointmentSlot = {
  id: string;
  startsAt: string;
  endsAt: string;
  remainingCapacity: number;
  capacity: number;
  locationI18n: Record<string, string>;
  status: string;
};

export type Appointment = {
  id: string;
  status: string;
  slot: AppointmentSlot;
};

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

export async function apiUpload<T>(token: string, path: string, form: FormData): Promise<T> {
  return apiJson<T>(token, path, { method: "POST", body: form });
}

async function apiJson<T>(token: string, path: string, init: RequestInit): Promise<T> {
  let response: Response;
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  try {
    response = await fetch(`${base}${path}`, {
      ...init,
      headers,
    });
  } catch {
    throw new ApiError(0, "error.network");
  }
  const json = (await response.json().catch(() => null)) as { messageKey?: string } | null;
  if (!response.ok) {
    throw new ApiError(
      response.status,
      json?.messageKey ?? (response.status === 401 ? "error.session_expired" : "error.http"),
    );
  }
  if (json == null) {
    throw new ApiError(response.status, "error.http");
  }
  return json as T;
}

export async function downloadDocument(token: string, caseId: string, documentId: string, filename: string) {
  const response = await fetch(`${base}/api/v1/cases/${caseId}/documents/${documentId}/content`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 401 ? "error.session_expired" : "error.http");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export async function completeMockPayment(providerReference: string) {
  const response = await fetch(`${base}/api/v1/payments/webhooks/MOCK`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      providerReference,
      idempotencyKey: crypto.randomUUID(),
      event: "payment.completed",
    }),
  });
  if (!response.ok) {
    throw new ApiError(response.status, "error.payment.webhook_invalid");
  }
}

export function publishedVersion(procedure: Procedure | undefined): ProcedureVersion | undefined {
  return procedure?.versions.find((version) => version.status === "PUBLISHED") ?? procedure?.versions[0];
}

export function loc(map: Record<string, string> | undefined, language: string, fallback = "—"): string {
  const lang = language.slice(0, 2);
  return map?.[lang] || map?.fr || map?.en || map?.pt || fallback;
}
