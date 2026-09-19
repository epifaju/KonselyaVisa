import { useEffect, useRef } from "react";
import { apiPost, type ApiResponse, type CaseItem } from "@/api/client";
import { clearGuestTicket, readGuestTicket } from "@/lib/guestTicket";

type Props = {
  token: string;
  onCreated: (id: string) => void;
};

export function GuestTicketAttach({ token, onCreated }: Props) {
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) {
      return;
    }
    const draft = readGuestTicket();
    if (!draft) {
      return;
    }
    started.current = true;
    void apiPost<ApiResponse<CaseItem>>(token, "/api/v1/cases", {
      procedureDefinitionId: draft.procedureDefinitionId,
      applicant: { facts: {} },
      privacyConsent: {
        accepted: true,
        locale: draft.locale,
        noticeVersion: draft.noticeVersion,
      },
      eligibilityTicketId: draft.ticketId,
    })
      .then((response) => {
        clearGuestTicket();
        if (response.data?.id) {
          onCreated(response.data.id);
        }
      })
      .catch(() => {
        clearGuestTicket();
        started.current = false;
      });
  }, [token, onCreated]);

  return null;
}
