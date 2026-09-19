export function flagEmoji(isoCode: string): string {
  const iso = isoCode.trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(iso)) {
    return "";
  }
  return String.fromCodePoint(...[...iso].map((letter) => 0x1f1e6 - 65 + letter.charCodeAt(0)));
}
