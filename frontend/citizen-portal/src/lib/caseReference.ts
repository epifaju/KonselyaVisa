export function shortCaseReference(reference: string | undefined): string {
  if (!reference) {
    return "";
  }
  const prefix = reference.match(/^[A-Za-z]+/)?.[0]?.toUpperCase() ?? "KV";
  const digits = reference.replace(/\D/g, "");
  const tail = (digits.length >= 4 ? digits.slice(-4) : digits.padStart(4, "0"));
  return `${prefix}-${tail}`;
}
