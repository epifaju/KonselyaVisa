const KEY = "kv.guestEligibilityTicket";

export type GuestTicketDraft = {
  ticketId: string;
  procedureDefinitionId: string;
  locale: string;
  noticeVersion?: string;
};

export function saveGuestTicket(draft: GuestTicketDraft) {
  sessionStorage.setItem(KEY, JSON.stringify(draft));
}

export function readGuestTicket(): GuestTicketDraft | null {
  const raw = sessionStorage.getItem(KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as GuestTicketDraft;
    if (!parsed.ticketId || !parsed.procedureDefinitionId) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearGuestTicket() {
  sessionStorage.removeItem(KEY);
}
