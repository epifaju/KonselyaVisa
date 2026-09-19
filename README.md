# KonselyaVisa — Bloc 1 foundations

## CI

Chaque pull request (et push sur `main` / `master`) lance GitHub Actions **verify** (`mvn verify`, dont `OpenApiContractIT`) et **supply-chain** (SBOM CycloneDX `target/bom.json`, Trivy fs + image API, échec sur vulnérabilités **CRITICAL** non corrigées). Un échec doit bloquer le merge — *Settings → Branches → Require status checks → `verify` et `supply-chain`*.

En local, depuis `backend/konselyavisa-api` (Docker requis pour les IT) :

```
./mvnw verify
```

Windows : `mvnw.cmd verify`.

OpenAPI : `http://localhost:18083/v3/api-docs` (aussi via nginx `http://localhost:18080/v3/api-docs`) et Swagger UI `http://localhost:18083/swagger-ui.html` (spec uniquement ; les appels métier restent authentifiés). Pour étendre le contrat, ajoutez l’opération dans le JSON **et** dans le contrôleur.

Le collecteur OTel n’exporte plus en debug : traces → Tempo, métriques OTLP → Prometheus (`:8889`) ; Spring expose aussi `/actuator/prometheus` (scrape interne, pas proxifié par nginx). SBOM local : `./mvnw -DskipTests package` → `target/classes/META-INF/sbom/application.cdx.json`.

## RGPD

Le registre des traitements MVP est dans [`docs/REGISTRE_TRAITEMENTS.md`](docs/REGISTRE_TRAITEMENTS.md). Le citoyen consent au dépôt du dossier ; `GET /api/v1/me/data-export` et `POST /api/v1/me/data-deletion-request` (anonymisation différée).

## Start the stack

From the repository root:

```
docker compose -f infrastructure/docker-compose.yml up --build
```

Host ports are offset to avoid collisions with other local stacks:

- API (direct): http://localhost:18083/actuator/health
- API (nginx): http://localhost:18080/actuator/health — `/api/` proxied to Spring
- Keycloak: http://localhost:8081 (admin / admin)
- MinIO console: http://localhost:9011
- n8n: http://localhost:15678 (`n8n@konselyavisa.local` / `N8nDev!23`)
- Mailpit: http://localhost:8029
- Grafana: http://localhost:13000 (`admin` / `admin`) — Prometheus + Tempo
- Prometheus: http://localhost:19090
- Tempo: http://localhost:13200

Outbox events (`CASE_CREATED`, documents, paiement, RDV, correction) are published by Spring Boot to `http://n8n:5678/webhook/konselyavisa-outbox`. n8n only sends a notification email to Mailpit — it never changes case status, eligibility, or pricing.

- Postgres: localhost:5438
- Redis: localhost:6388 (holds de créneau 2 min, cache catalogue, rate-limit API)

Realm `konselyavisa` demo users (see `.env.example`):
- `citizen.dev` / `CitizenDev!23`
- `company.dev` / `CompanyDev!23` (espace entreprise, ses dossiers)
- `company.admin.dev` / `CompanyAdminDev!23` (tous les dossiers entreprise de l’org)
- `agent.dev` / `AgentDev!23`
- `admin.dev` / `AdminDev!23`

## Citizen portal (local)

```
cd frontend/citizen-portal
cp .env.example .env
npm install
npm run dev
```

Open http://localhost:5175 and sign in as `citizen.dev` / `CitizenDev!23`.
From there you can create a visa case, upload documents, pay (MOCK by default, Stripe when the org is switched), and book a slot.

Company workspace (same portal): `company.dev` / `CompanyDev!23` sees a dense list with status counters and a creator column; `company.admin.dev` sees every company-created case in the org. Re-import the Keycloak realm if those users are missing.

## Agent back-office (local)

```
cd frontend/admin-portal
cp .env.example .env
npm install
npm run dev
```

Open http://localhost:5176 and sign in as `agent.dev` / `AgentDev!23`.
The Keycloak client `agent-portal` is defined in `infrastructure/keycloak/konselyavisa-realm.json` (re-import the realm if the container was created before this client existed).

## n8n (notifications around Spring state)

`n8n-init` creates the owner, a Mailpit SMTP credential, and the workflow **KonselyaVisa outbox notify** (production webhook `POST /webhook/konselyavisa-outbox`).

- UI: http://localhost:15678 — `n8n@konselyavisa.local` / `N8nDev!23`
- Inbox: http://localhost:8029 — mail to `notify@konselyavisa.local`

Spring remains the only source of eligibility, tariffs, and case status. n8n only emails the outbox payload. The publisher signs requests with `X-Konselya-Signature` (`KONSELYAVISA_OUTBOX_WEBHOOK_SECRET`).

## Stripe

The default demo org still uses `paymentProvider: MOCK`. To take a real card:

1. Set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` (see `.env.example`).
2. Set `organization_settings.settings.paymentProvider` to `"STRIPE"`.
3. Enable `feature_flags` key `payment.provider.STRIPE` for that org (global seed is off; demo org is on). If the flag is off, checkout falls back to MOCK.
4. Forward events: `stripe listen --forward-to localhost:18083/api/v1/payments/webhooks/STRIPE`.

Checkout, verify and webhooks stay inside `StripePaymentProvider` / `LiveStripeGateway`. Completing a payment still only happens in Spring (`PAYMENT_COMPLETED` outbox). After returning from Checkout, the citizen can click **Confirm Stripe payment** (`POST /api/v1/payments/{id}/sync`) if the webhook has not arrived yet.

## CinetPay

Mobile money / cartes Afrique de l’Ouest. n8n ne décide jamais du statut de paiement.

1. Set `CINETPAY_API_KEY`, `CINETPAY_SITE_ID`, `CINETPAY_SECRET_KEY` (see `.env.example`).
2. Set `organization_settings.settings.paymentProvider` to `"CINETPAY"`.
3. Enable `feature_flags` key `payment.provider.CINETPAY` (global seed is off; demo org is on). Flag off → checkout falls back to MOCK.
4. Point the CinetPay notify URL to `http://localhost:18083/api/v1/payments/webhooks/CINETPAY` (HMAC + `payment/check` before completing).

The citizen is sent to CinetPay `payment_url`. Spring records `PAYMENT_COMPLETED` once, even if CinetPay retries the IPN.
