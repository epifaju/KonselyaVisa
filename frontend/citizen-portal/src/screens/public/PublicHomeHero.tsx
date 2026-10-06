type Props = {
  organizationName: string;
  logoUrl?: string | null;
  title: string;
  subtitle: string;
  ctaTitle: string;
  startLabel: string;
  trackLabel: string;
  onStart: () => void;
  onTrack: () => void;
};

/** Full-bleed photo hero: brand, headline, supporting line, CTA panel. */
export function PublicHomeHero({
  organizationName,
  logoUrl,
  title,
  subtitle,
  ctaTitle,
  startLabel,
  trackLabel,
  onStart,
  onTrack,
}: Props) {
  return (
    <section className="relative isolate overflow-hidden text-primary-foreground">
      <img
        src="/images/hero-travel.jpg"
        alt=""
        className="absolute inset-0 h-full w-full object-cover"
      />
      <div className="home-hero-overlay absolute inset-0" aria-hidden />

      <div className="relative mx-auto flex min-h-[28rem] max-w-4xl flex-col items-center justify-center px-4 py-14 text-center md:min-h-[32rem] md:px-6 md:py-20">
        <div className="home-reveal flex flex-col items-center gap-3">
          {logoUrl ? (
            <img
              src={`${import.meta.env.VITE_API_BASE_URL ?? ""}${logoUrl}`}
              alt=""
              className="h-14 w-14 rounded-lg bg-primary-foreground/95 object-contain p-1 shadow-sm"
            />
          ) : (
            <div className="h-14 w-14 rounded-lg bg-primary-foreground/95 shadow-sm" aria-hidden />
          )}
          <p className="max-w-2xl text-heading-1 text-primary-foreground">{organizationName}</p>
        </div>

        <h1 className="home-reveal home-reveal-delay-1 mt-6 max-w-3xl text-display text-primary-foreground">
          {title}
        </h1>
        <p className="home-reveal home-reveal-delay-2 mx-auto mt-4 max-w-2xl text-body-lg text-primary-foreground/90">
          {subtitle}
        </p>

        <div className="home-reveal home-reveal-delay-3 mt-10 w-full max-w-lg rounded-lg bg-card p-6 text-card-foreground shadow-sm md:p-8">
          <p className="text-heading-2 font-medium text-primary">{ctaTitle}</p>
          <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:justify-center">
            <button
              type="button"
              className="inline-flex h-12 w-full items-center justify-center rounded-md bg-primary px-6 text-body-lg font-medium text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:w-auto"
              onClick={onStart}
            >
              {startLabel}
            </button>
            <button
              type="button"
              className="inline-flex h-12 w-full items-center justify-center rounded-md border border-border bg-background px-6 text-body-lg font-medium text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:w-auto"
              onClick={onTrack}
            >
              {trackLabel}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
