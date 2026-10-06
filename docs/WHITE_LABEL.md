# White-label SaaS (organisation)

KonselyaVisa brandise chaque organisation via `organization_settings` (JSONB) et des assets MinIO. Spring Boot reste la seule source de vérité ; n8n ne fait qu’orchestrer des e-mails HTML autour de `payload.branding`.

## Ce qui est couvert

| Capacité | Où | Notes |
| --- | --- | --- |
| Couleur de marque | `settings.brandColor` (`#RGB` / `#RRGGBB`) | Thème CSS `--primary` sur les portails |
| Domaine custom (résolution) | `settings.domain` | Lookup Host / `?domain=` → org active |
| Logo / favicon | MinIO `branding/{orgId}/logo\|favicon` + `*ContentType` | Upload admin ; GET public |
| E-mails brandés | Outbox → `payload.branding` → n8n HTML | Logo en URL absolue |
| DNS / TLS multi-tenant | Ops (nginx + certificats) | Hors runtime Spring — voir ci-dessous |

## Admin

- Rôles : `BUSINESS_ADMIN` ou `PLATFORM_ADMIN`.
- UI : portail agent → **Paramètres organisation** (couleur, logo, favicon, domaine, prestataire de paiement).
- API :
  - `PATCH /api/v1/organizations/me/settings` — `brandColor`, `domain`, `paymentProvider`
  - `POST|DELETE /api/v1/organizations/me/branding/{logo\|favicon}` — multipart `file`
  - `GET /api/v1/public/org` — branding public (couleur, URLs relatives logo/favicon)
  - `GET /api/v1/public/org/branding/{asset}?organizationId=` — octets image

Limites : logo 512 Ko, favicon 128 Ko ; types `image/png`, `image/jpeg`, `image/webp`, `image/x-icon`.

## Thème & assets front

Les portails citoyen et admin :

1. Résolvent l’org via JWT ou Host / `VITE` public org.
2. Appliquent `brandColor` (`useOrganizationBrandTheme`).
3. Remplacent le favicon (`useOrganizationBrandFavicon`) et affichent le logo dans les en-têtes / home publique.

Les URLs logo/favicon renvoyées par l’API sont **relatives** (`/api/v1/public/org/branding/...`) ; le front préfixe `VITE_API_BASE_URL`.

## E-mails (n8n)

À l’envoi outbox, `OutboxPublisher` enrichit le payload :

```json
"branding": {
  "organizationId": "...",
  "nameI18n": { "fr": "...", "en": "...", "pt": "..." },
  "brandColor": "#0B5D3B",
  "domain": "visa.acme.com",
  "logoUrl": "https://api.example.com/api/v1/public/org/branding/logo?organizationId=..."
}
```

Configurer `KONSELYAVISA_PUBLIC_BASE_URL` (ex. `https://api.example.com`) pour des logos absolus joignables depuis les clients mail. Workflow : `infrastructure/n8n/konselyavisa-outbox-notify.json` (HTML + texte, bandeau couleur + logo).

n8n **ne** décide pas d’éligibilité, tarif ou statut de dossier.

## Domaine custom — DNS / TLS (prod)

Le backend résout l’organisation à partir du `Host` (ou `domain=`). Il ne termine **pas** le TLS ni ne provisionne DNS.

Étapes ops typiques :

1. Le client pointe un enregistrement **CNAME** (ou A) de `visa.acme.com` vers le reverse-proxy KonselyaVisa.
2. Le proxy termine TLS (certificat Let’s Encrypt / ACME, ou certificat client) pour ce `server_name`.
3. Le proxy forward vers l’API / les portails en conservant `Host` (et idéalement `X-Forwarded-Proto`).
4. Dans l’admin, renseigner `domain=visa.acme.com` (hôte seul, sans schéma ni chemin).

Exemple nginx : `infrastructure/nginx/white-label.conf.example`.

### Hors scope runtime

- Provisioning automatique DNS / certificats multi-tenant
- E-mails « From: » alignés sur le domaine client (SPF/DKIM par org)
- CDN d’assets brandés hors MinIO + API publique

## Variables

| Variable | Rôle |
| --- | --- |
| `KONSELYAVISA_PUBLIC_BASE_URL` | Base absolue API pour `logoUrl` dans l’outbox (défaut local `http://localhost:18083`) |
| `VITE_API_BASE_URL` | Préfixe front pour logo/favicon relatifs |
