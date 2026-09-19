import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, apiPost, type ApiResponse, type AppointmentSlot } from "@/api/client";
import { Button } from "@/components/ui/button";

type Props = { token: string };

export function Slots({ token }: Props) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [capacity, setCapacity] = useState(2);
  const [location, setLocation] = useState("Consulat — guichet visas");

  const slotsQuery = useQuery({
    queryKey: ["slots"],
    queryFn: async () => (await apiGet<ApiResponse<AppointmentSlot[]>>(token, "/api/v1/appointment-slots")).data ?? [],
  });

  const create = useMutation({
    mutationFn: () =>
      apiPost(token, "/api/v1/appointment-slots", {
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        capacity,
        locationI18n: { fr: location, pt: location, en: location },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      setStartsAt("");
      setEndsAt("");
    },
  });

  const cancel = useMutation({
    mutationFn: (slotId: string) => apiPost(token, `/api/v1/appointment-slots/${slotId}/cancel`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["slots"] }),
  });

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };

  const lang = i18n.language.slice(0, 2);

  return (
    <section className="space-y-6">
      <h2 className="text-xl font-semibold">{t("slots.title")}</h2>
      <form onSubmit={onSubmit} className="grid gap-3 rounded-lg border bg-card p-4 md:grid-cols-2">
        <label className="text-sm">
          {t("slots.startsAt")}
          <input
            required
            type="datetime-local"
            className="mt-1 w-full rounded-md border bg-background px-2 py-1"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />
        </label>
        <label className="text-sm">
          {t("slots.endsAt")}
          <input
            required
            type="datetime-local"
            className="mt-1 w-full rounded-md border bg-background px-2 py-1"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </label>
        <label className="text-sm">
          {t("slots.capacity")}
          <input
            required
            min={1}
            type="number"
            className="mt-1 w-full rounded-md border bg-background px-2 py-1"
            value={capacity}
            onChange={(event) => setCapacity(Number(event.target.value))}
          />
        </label>
        <label className="text-sm">
          {t("slots.location")}
          <input
            required
            className="mt-1 w-full rounded-md border bg-background px-2 py-1"
            value={location}
            onChange={(event) => setLocation(event.target.value)}
          />
        </label>
        <div className="md:col-span-2">
          <Button type="submit" disabled={create.isPending}>
            {t("slots.create")}
          </Button>
          {create.isError ? <p className="mt-2 text-sm text-red-700">{t("common.error")}</p> : null}
        </div>
      </form>
      <ul className="space-y-2">
        {(slotsQuery.data ?? []).map((slot) => (
          <li key={slot.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border bg-card p-3">
            <div className="text-sm">
              <p className="font-medium">{slot.locationI18n[lang] ?? slot.locationI18n.fr ?? slot.id}</p>
              <p className="text-muted-foreground">
                {new Date(slot.startsAt).toLocaleString()} → {new Date(slot.endsAt).toLocaleString()} ·{" "}
                {t("slots.remaining", { remaining: slot.remainingCapacity, capacity: slot.capacity })} ·{" "}
                {t(`status.slot.${slot.status}`)}
              </p>
            </div>
            {slot.status === "OPEN" ? (
              <Button type="button" size="sm" variant="outline" onClick={() => cancel.mutate(slot.id)}>
                {t("slots.cancel")}
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
