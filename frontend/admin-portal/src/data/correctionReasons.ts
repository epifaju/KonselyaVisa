export type CorrectionReason = {
  key: string;
  codes: string[];
};

export const CORRECTION_REASONS: CorrectionReason[] = [
  { key: "document.correction.unreadable", codes: ["PASSPORT", "PHOTO", "ORIGINAL_DOCUMENT", "ID_COPY"] },
  { key: "document.correction.incomplete", codes: ["PASSPORT", "ORIGINAL_DOCUMENT", "ID_COPY"] },
  { key: "document.correction.expired", codes: ["PASSPORT", "ID_COPY"] },
  { key: "document.correction.photo_spec", codes: ["PHOTO"] },
  { key: "document.correction.mismatch", codes: ["PASSPORT", "ORIGINAL_DOCUMENT"] },
];

export function reasonsFor(requirementCode: string): CorrectionReason[] {
  const matched = CORRECTION_REASONS.filter((reason) => reason.codes.includes(requirementCode));
  return matched.length > 0 ? matched : CORRECTION_REASONS.filter((reason) => reason.key.endsWith("unreadable"));
}
