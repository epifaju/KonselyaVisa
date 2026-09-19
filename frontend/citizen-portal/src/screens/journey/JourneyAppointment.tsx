import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { loc, type Appointment, type AppointmentSlot } from "@/api/client";
import { ChoiceCard } from "@/components/ChoiceCard";
import { Button } from "@/components/ui/button";

type Props = {
  booked: Appointment | undefined;
  slots: AppointmentSlot[];
  language: string;
  busy?: boolean;
  onBook: (slotId: string) => void;
  onCancel?: (appointmentId: string) => void;
  allowCancel?: boolean;
};

export function JourneyAppointment({
  booked,
  slots,
  language,
  busy,
  onBook,
  onCancel,
  allowCancel = false,
}: Props) {
  const { t } = useTranslation();
  const [selectedId, setSelectedId] = useState<string>(slots.find((slot) => slot.remainingCapacity >= 1)?.id ?? "");
  const selected = slots.find((slot) => slot.id === selectedId);

  const byDay = useMemo(() => {
    const groups = new Map<string, AppointmentSlot[]>();
    for (const slot of slots) {
      const key = dayKey(slot.startsAt, language);
      const list = groups.get(key) ?? [];
      list.push(slot);
      groups.set(key, list);
    }
    return [...groups.entries()];
  }, [slots, language]);

  if (booked) {
    return (
      <div className="space-y-2">
        <p className="text-body text-foreground">
          {slotHeadline(booked.slot.startsAt, language)} · {loc(booked.slot.locationI18n, language)}
        </p>
        {allowCancel && onCancel ? (
          <Button type="button" size="sm" variant="outline" onClick={() => onCancel(booked.id)}>
            {t("journey.cancelAppointment")}
          </Button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {byDay.map(([day, daySlots]) => (
        <div key={day} className="space-y-2">
          {daySlots.map((slot) => (
            <ChoiceCard
              key={slot.id}
              selected={selectedId === slot.id}
              disabled={slot.remainingCapacity < 1}
              title={slotHeadline(slot.startsAt, language)}
              description={t("journey.placesAvailable", { count: slot.remainingCapacity })}
              onSelect={() => setSelectedId(slot.id)}
            />
          ))}
        </div>
      ))}
      {slots.length === 0 ? <p className="text-body-sm text-muted-foreground">{t("journey.noSlots")}</p> : null}
      {selected ? (
        <Button
          type="button"
          className="w-full"
          disabled={busy || selected.remainingCapacity < 1}
          onClick={() => onBook(selected.id)}
        >
          {t("journey.confirmThisAppointment")}
        </Button>
      ) : null}
    </div>
  );
}

export function appointmentLocation(slots: AppointmentSlot[], language: string): string | undefined {
  const first = slots[0];
  return first ? loc(first.locationI18n, language) : undefined;
}

function dayKey(iso: string, language: string) {
  return new Intl.DateTimeFormat(language, { dateStyle: "full" }).format(new Date(iso));
}

export function slotHeadline(iso: string, language: string): string {
  const date = new Date(iso);
  const day = new Intl.DateTimeFormat(language, { weekday: "long", day: "numeric", month: "long" }).format(date);
  const time = new Intl.DateTimeFormat(language, { hour: "2-digit", minute: "2-digit" }).format(date);
  const labeled = day.charAt(0).toUpperCase() + day.slice(1);
  return `${labeled} — ${time}`;
}
