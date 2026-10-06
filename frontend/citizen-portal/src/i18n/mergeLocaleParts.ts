function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

export function mergeLocaleParts(
  ...parts: Record<string, unknown>[]
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const part of parts) {
    for (const [key, value] of Object.entries(part)) {
      const existing = result[key];
      if (isPlainObject(existing) && isPlainObject(value)) {
        result[key] = mergeLocaleParts(existing, value);
      } else {
        result[key] = value;
      }
    }
  }
  return result;
}
