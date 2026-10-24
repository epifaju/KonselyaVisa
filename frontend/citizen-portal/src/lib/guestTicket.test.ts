import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearGuestTicket, readGuestTicket, saveGuestTicket } from "./guestTicket";

describe("guestTicket session helpers", () => {
  const store = new Map<string, string>();

  beforeEach(() => {
    store.clear();
    vi.stubGlobal("sessionStorage", {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => {
        store.set(key, value);
      },
      removeItem: (key: string) => {
        store.delete(key);
      },
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("round-trips a valid draft", () => {
    saveGuestTicket({
      ticketId: "t-1",
      procedureDefinitionId: "p-1",
      locale: "fr",
      noticeVersion: "v1",
    });
    expect(readGuestTicket()).toEqual({
      ticketId: "t-1",
      procedureDefinitionId: "p-1",
      locale: "fr",
      noticeVersion: "v1",
    });
  });

  it("returns null for missing or corrupt payloads", () => {
    expect(readGuestTicket()).toBeNull();
    store.set("kv.guestEligibilityTicket", "{");
    expect(readGuestTicket()).toBeNull();
    store.set("kv.guestEligibilityTicket", JSON.stringify({ ticketId: "only" }));
    expect(readGuestTicket()).toBeNull();
  });

  it("clears the stored ticket", () => {
    saveGuestTicket({ ticketId: "t-1", procedureDefinitionId: "p-1", locale: "fr" });
    clearGuestTicket();
    expect(readGuestTicket()).toBeNull();
  });
});
